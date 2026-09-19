package per.kokabot.maimai.asset;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import per.kokabot.maimai.model.B50Result;
import per.kokabot.maimai.model.ScoreRecord;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

@Service
public class MaimaiAssetService {

    private static final Logger log =
            LoggerFactory.getLogger(MaimaiAssetService.class);
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(15);
    private static final int MAX_JACKET_BYTES = 5 * 1024 * 1024;

    private final ObjectMapper objectMapper;
    private final Executor assetExecutor;
    private final HttpClient httpClient;
    private final String lxnsApiBaseUrl;
    private final String jacketBaseUrl;
    private final String fallbackJacketBaseUrl;
    private final Path rootDirectory;
    private final Path jacketsDirectory;
    private final Path catalogFile;
    private final Duration catalogRefreshInterval;
    private final ConcurrentHashMap<String, CompletableFuture<Optional<String>>>
            jacketRequests = new ConcurrentHashMap<>();
    private volatile CatalogSnapshot catalogSnapshot;

    public MaimaiAssetService(
            ObjectMapper objectMapper,
            @Qualifier("maimaiAssetExecutor") Executor assetExecutor,
            @Value("${maimai.provider.lxns.base-url}") String lxnsApiBaseUrl,
            @Value("${maimai.assets.jacket-base-url}") String jacketBaseUrl,
            @Value("${maimai.assets.fallback-jacket-base-url}")
            String fallbackJacketBaseUrl,
            @Value("${maimai.assets.root-directory}") String rootDirectory,
            @Value("${maimai.assets.catalog-refresh-hours:24}") long refreshHours
    ) {
        this.objectMapper = objectMapper;
        this.assetExecutor = assetExecutor;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(HTTP_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.lxnsApiBaseUrl = stripTrailingSlash(lxnsApiBaseUrl);
        this.jacketBaseUrl = stripTrailingSlash(jacketBaseUrl);
        this.fallbackJacketBaseUrl = stripTrailingSlash(
                fallbackJacketBaseUrl
        );
        this.rootDirectory = Path.of(rootDirectory)
                .toAbsolutePath()
                .normalize();
        this.jacketsDirectory = this.rootDirectory.resolve("jackets");
        this.catalogFile = this.rootDirectory
                .resolve("catalog")
                .resolve("song-list.json");
        this.catalogRefreshInterval = Duration.ofHours(
                Math.max(1, refreshHours)
        );
    }

    @EventListener(ApplicationReadyEvent.class)
    public void refreshCatalogAfterStartup() {
        CompletableFuture.runAsync(this::refreshCatalogIfNeeded, assetExecutor)
                .exceptionally(error -> {
                    log.warn("舞萌曲库同步失败，将继续使用现有快照", error);
                    return null;
                });
    }

    public CompletableFuture<Map<String, String>> loadJacketDataUris(
            B50Result result
    ) {
        List<String> songIds = collectSongIds(result);
        Map<String, CompletableFuture<Optional<String>>> requests =
                new LinkedHashMap<>();

        for (String songId : songIds) {
            requests.put(songId, loadJacketDataUri(songId));
        }

        CompletableFuture<?>[] futures = requests.values()
                .toArray(CompletableFuture[]::new);

        return CompletableFuture.allOf(futures)
                .thenApply(ignored -> {
                    Map<String, String> images = new LinkedHashMap<>();
                    requests.forEach((songId, request) ->
                            request.join().ifPresent(dataUri ->
                                    images.put(songId, dataUri)
                            )
                    );
                    return Map.copyOf(images);
                });
    }

    public CompletableFuture<B50Result> enrichScoreMetadata(B50Result result) {
        return CompletableFuture.supplyAsync(
                () -> enrichScoreMetadataSync(result),
                assetExecutor
        );
    }

    public Path rootDirectory() {
        return rootDirectory;
    }

    private CompletableFuture<Optional<String>> loadJacketDataUri(
            String rawSongId
    ) {
        Optional<String> normalized = normalizeSongId(rawSongId);
        if (normalized.isEmpty()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        String songId = normalized.get();
        CompletableFuture<Optional<String>> request =
                jacketRequests.computeIfAbsent(
                        songId,
                        ignored -> CompletableFuture.supplyAsync(
                                () -> loadJacketDataUriSync(songId),
                                assetExecutor
                        ).exceptionally(error -> {
                            log.warn(
                                    "曲绘获取失败 songId={}",
                                    songId,
                                    error
                            );
                            return Optional.empty();
                        })
                );
        request.whenComplete((value, error) ->
                jacketRequests.remove(songId, request)
        );
        return request;
    }

    private Optional<String> loadJacketDataUriSync(String songId) {
        try {
            Files.createDirectories(jacketsDirectory);
            Path jacketFile = jacketsDirectory.resolve(songId + ".png");
            byte[] bytes;

            if (Files.isRegularFile(jacketFile)) {
                bytes = Files.readAllBytes(jacketFile);
                if (bytes.length == 0
                        || bytes.length > MAX_JACKET_BYTES
                        || !isPng(bytes)) {
                    log.warn("检测到无效曲绘缓存，将重新下载 path={}", jacketFile);
                    bytes = downloadJacket(songId);
                    writeAtomically(jacketFile, bytes);
                }
            } else {
                bytes = downloadJacket(songId);
                writeAtomically(jacketFile, bytes);
            }

            return Optional.of(
                    "data:image/png;base64,"
                            + Base64.getEncoder().encodeToString(bytes)
            );
        } catch (Exception error) {
            throw new IllegalStateException(
                    "无法读取或缓存曲绘 songId=" + songId,
                    error
            );
        }
    }

    private byte[] downloadJacket(String songId) throws Exception {
        List<String> candidates = jacketIdCandidates(songId);
        String lastFailure = "没有可用候选地址";

        for (String candidate : candidates) {
            JacketDownload download = requestJacket(
                    jacketBaseUrl + "/" + candidate + ".png"
            );
            if (download.valid()) {
                return download.body();
            }
            lastFailure = candidate + ".png HTTP "
                    + download.statusCode();
        }

        String fallbackId = fallbackJacketId(songId);
        JacketDownload fallback = requestJacket(
                fallbackJacketBaseUrl + "/" + fallbackId + ".png"
        );
        if (fallback.valid()) {
            log.info(
                    "水鱼曲绘缺失，已使用备用源 songId={}",
                    songId
            );
            return fallback.body();
        }

        throw new IOException(
                "曲绘响应无效：水鱼 " + lastFailure
                        + "，备用源 " + fallbackId + ".png HTTP "
                        + fallback.statusCode()
        );
    }

    private JacketDownload requestJacket(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(HTTP_TIMEOUT)
                .header("Accept", "image/png")
                .header("User-Agent", "KokaBot/1.0")
                .GET()
                .build();
        HttpResponse<byte[]> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofByteArray()
        );
        byte[] body = response.body();
        boolean valid = response.statusCode() == 200
                && body.length > 0
                && body.length <= MAX_JACKET_BYTES
                && isPng(body);
        return new JacketDownload(response.statusCode(), body, valid);
    }

    private synchronized void refreshCatalogIfNeeded() {
        try {
            if (isCatalogFresh()) {
                return;
            }

            URI uri = URI.create(
                    lxnsApiBaseUrl + "/maimai/song/list?notes=true"
            );
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(HTTP_TIMEOUT)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );

            if (response.statusCode() < 200
                    || response.statusCode() >= 300) {
                throw new IOException(
                        "曲库接口返回 HTTP " + response.statusCode()
                );
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode songs = root.path("songs");
            if (!songs.isArray()) {
                throw new IOException("曲库接口响应结构无效");
            }

            writeAtomically(
                    catalogFile,
                    objectMapper.writerWithDefaultPrettyPrinter()
                            .writeValueAsBytes(root)
            );
            log.info(
                    "舞萌曲库已同步 songs={} path={}",
                    songs.size(),
                    catalogFile
            );
        } catch (Exception error) {
            throw new IllegalStateException("舞萌曲库同步失败", error);
        }
    }

    private B50Result enrichScoreMetadataSync(B50Result result) {
        Map<ChartKey, ChartMetadata> metadata = loadChartMetadata();
        return new B50Result(
                result.playerName(),
                result.rating(),
                enrichScores(result.best35(), metadata),
                enrichScores(result.best15(), metadata)
        );
    }

    private List<ScoreRecord> enrichScores(
            List<ScoreRecord> scores,
            Map<ChartKey, ChartMetadata> metadata
    ) {
        if (scores == null) {
            return List.of();
        }
        return scores.stream()
                .map(score -> enrichScore(score, metadata))
                .toList();
    }

    private ScoreRecord enrichScore(
            ScoreRecord score,
            Map<ChartKey, ChartMetadata> metadata
    ) {
        ChartMetadata chart = findChartMetadata(score, metadata);
        double chartConstant = score.chartConstant();
        int dxStar = score.dxStar();

        if (chart != null) {
            if (chartConstant <= 0) {
                chartConstant = chart.levelValue();
            }
            if (dxStar <= 0 && chart.maxDxScore() > 0) {
                dxStar = calculateDxStar(score.dxScore(), chart.maxDxScore());
            }
        }

        return new ScoreRecord(
                score.songId(),
                score.songName(),
                score.chartType(),
                score.difficulty(),
                score.level(),
                chartConstant,
                score.achievement(),
                score.dxScore(),
                score.rating(),
                dxStar,
                score.fullCombo(),
                score.fullSync()
        );
    }

    private ChartMetadata findChartMetadata(
            ScoreRecord score,
            Map<ChartKey, ChartMetadata> metadata
    ) {
        try {
            long songId = Long.parseLong(score.songId());
            if (songId >= 10_000 && songId < 100_000) {
                songId %= 10_000;
            }
            int difficulty = difficultyIndex(score.difficulty());
            if (difficulty < 0) {
                return null;
            }
            return metadata.get(new ChartKey(
                    songId,
                    normalizeChartType(score.chartType()),
                    difficulty
            ));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Map<ChartKey, ChartMetadata> loadChartMetadata() {
        try {
            refreshCatalogIfNeeded();
        } catch (RuntimeException error) {
            log.warn("曲库刷新失败，尝试使用现有快照补全谱面信息", error);
        }

        if (!Files.isRegularFile(catalogFile)) {
            return Map.of();
        }

        try {
            FileTime modifiedTime = Files.getLastModifiedTime(catalogFile);
            CatalogSnapshot current = catalogSnapshot;
            if (current != null && current.modifiedTime().equals(modifiedTime)) {
                return current.charts();
            }

            JsonNode songs = objectMapper.readTree(catalogFile.toFile())
                    .path("songs");
            Map<ChartKey, ChartMetadata> charts = new HashMap<>();
            for (JsonNode song : songs) {
                long songId = song.path("id").asLong(-1);
                JsonNode difficulties = song.path("difficulties");
                addChartMetadata(
                        charts,
                        songId,
                        "SD",
                        difficulties.path("standard")
                );
                addChartMetadata(
                        charts,
                        songId,
                        "DX",
                        difficulties.path("dx")
                );
            }
            Map<ChartKey, ChartMetadata> immutable = Map.copyOf(charts);
            catalogSnapshot = new CatalogSnapshot(modifiedTime, immutable);
            return immutable;
        } catch (IOException error) {
            log.warn("读取本地曲库失败，谱面定数和 DX 星级可能不完整", error);
            return Map.of();
        }
    }

    private void addChartMetadata(
            Map<ChartKey, ChartMetadata> target,
            long songId,
            String chartType,
            JsonNode difficulties
    ) {
        if (songId < 0 || !difficulties.isArray()) {
            return;
        }
        for (JsonNode difficulty : difficulties) {
            int difficultyIndex = difficulty.path("difficulty").asInt(-1);
            double levelValue = difficulty.path("level_value").asDouble(0);
            int noteCount = difficulty.path("notes").path("total").asInt(0);
            if (difficultyIndex >= 0) {
                target.put(
                        new ChartKey(songId, chartType, difficultyIndex),
                        new ChartMetadata(levelValue, noteCount * 3)
                );
            }
        }
    }

    private int calculateDxStar(int dxScore, int maxDxScore) {
        double ratio = (double) dxScore / maxDxScore;
        if (ratio >= 0.97) {
            return 5;
        }
        if (ratio >= 0.95) {
            return 4;
        }
        if (ratio >= 0.93) {
            return 3;
        }
        if (ratio >= 0.90) {
            return 2;
        }
        return ratio >= 0.85 ? 1 : 0;
    }

    private int difficultyIndex(String difficulty) {
        if (difficulty == null) {
            return -1;
        }
        return switch (difficulty.toLowerCase()) {
            case "basic" -> 0;
            case "advanced" -> 1;
            case "expert" -> 2;
            case "master" -> 3;
            case "re:master", "remaster" -> 4;
            default -> -1;
        };
    }

    private String normalizeChartType(String chartType) {
        if (chartType == null) {
            return "";
        }
        return switch (chartType.toLowerCase()) {
            case "standard", "sd" -> "SD";
            case "dx" -> "DX";
            default -> chartType.toUpperCase();
        };
    }

    private boolean isCatalogFresh() throws IOException {
        if (!Files.isRegularFile(catalogFile)) {
            return false;
        }
        FileTime modifiedTime = Files.getLastModifiedTime(catalogFile);
        Instant expiresAt = modifiedTime.toInstant()
                .plus(catalogRefreshInterval);
        return expiresAt.isAfter(Instant.now());
    }

    private void writeAtomically(Path target, byte[] bytes)
            throws IOException {
        Files.createDirectories(target.getParent());
        Path temporary = target.resolveSibling(
                target.getFileName() + ".tmp"
        );
        Files.write(temporary, bytes);
        try {
            Files.move(
                    temporary,
                    target,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
            );
        } catch (IOException unsupportedAtomicMove) {
            Files.move(
                    temporary,
                    target,
                    StandardCopyOption.REPLACE_EXISTING
            );
        }
    }

    private List<String> collectSongIds(B50Result result) {
        List<String> ids = new ArrayList<>(50);
        addSongIds(ids, result.best35());
        addSongIds(ids, result.best15());
        return ids.stream().distinct().toList();
    }

    private void addSongIds(
            List<String> target,
            List<ScoreRecord> scores
    ) {
        if (scores == null) {
            return;
        }
        scores.stream()
                .map(ScoreRecord::songId)
                .filter(id -> id != null && !id.isBlank())
                .forEach(target::add);
    }

    private Optional<String> normalizeSongId(String rawSongId) {
        try {
            long id = Long.parseLong(rawSongId.trim());
            if (id < 0) {
                return Optional.empty();
            }
            return Optional.of(Long.toString(id));
        } catch (NumberFormatException error) {
            return Optional.empty();
        }
    }

    private List<String> jacketIdCandidates(String songId) {
        long id = Long.parseLong(songId);
        if (id >= 10_000) {
            return List.of(songId);
        }
        return List.of(
                String.format("%05d", id),
                Long.toString(id + 10_000)
        );
    }

    private String fallbackJacketId(String songId) {
        long id = Long.parseLong(songId);
        if (id >= 10_000 && id < 100_000) {
            id %= 10_000;
        }
        return String.format("%06d", id);
    }

    private boolean isPng(byte[] bytes) {
        byte[] signature = {
                (byte) 0x89, 0x50, 0x4e, 0x47,
                0x0d, 0x0a, 0x1a, 0x0a
        };
        if (bytes.length < signature.length) {
            return false;
        }
        for (int index = 0; index < signature.length; index++) {
            if (bytes[index] != signature[index]) {
                return false;
            }
        }
        return true;
    }

    private String stripTrailingSlash(String value) {
        String result = value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private record JacketDownload(
            int statusCode,
            byte[] body,
            boolean valid
    ) {
    }

    private record ChartKey(long songId, String chartType, int difficulty) {
    }

    private record ChartMetadata(double levelValue, int maxDxScore) {
    }

    private record CatalogSnapshot(
            FileTime modifiedTime,
            Map<ChartKey, ChartMetadata> charts
    ) {
    }
}
