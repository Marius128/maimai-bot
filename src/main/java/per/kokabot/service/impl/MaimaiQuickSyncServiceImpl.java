package per.kokabot.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import per.kokabot.maimai.adapter.MaimaiAdapterClient;
import per.kokabot.provider.MaimaiProviderType;
import per.kokabot.provider.lxns.LxnsUserScoreClient;
import per.kokabot.service.DivingFishBindingService;
import per.kokabot.service.LxnsOAuthBindingService;
import per.kokabot.service.MaimaiAccountBindingService;
import per.kokabot.service.MaimaiProviderPreferenceService;
import per.kokabot.service.MaimaiQuickSyncService;
import per.kokabot.service.MaimaiSyncTargetException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

@Service
public class MaimaiQuickSyncServiceImpl implements MaimaiQuickSyncService {

    private static final double ACHIEVEMENT_EPSILON = 0.000001D;

    private final MaimaiAdapterClient adapterClient;
    private final LxnsUserScoreClient lxnsScoreClient;
    private final DivingFishBindingService divingFishBindingService;
    private final MaimaiAccountBindingService maimaiBindingService;
    private final LxnsOAuthBindingService lxnsBindingService;
    private final MaimaiProviderPreferenceService preferenceService;
    private final Executor syncExecutor;
    private final Set<Long> activeUsers = ConcurrentHashMap.newKeySet();

    public MaimaiQuickSyncServiceImpl(
            MaimaiAdapterClient adapterClient,
            LxnsUserScoreClient lxnsScoreClient,
            DivingFishBindingService divingFishBindingService,
            MaimaiAccountBindingService maimaiBindingService,
            LxnsOAuthBindingService lxnsBindingService,
            MaimaiProviderPreferenceService preferenceService,
            @Qualifier("maimaiSyncExecutor") Executor syncExecutor
    ) {
        this.adapterClient = adapterClient;
        this.lxnsScoreClient = lxnsScoreClient;
        this.divingFishBindingService = divingFishBindingService;
        this.maimaiBindingService = maimaiBindingService;
        this.lxnsBindingService = lxnsBindingService;
        this.preferenceService = preferenceService;
        this.syncExecutor = syncExecutor;
    }

    @Override
    public CompletableFuture<SyncResult> sync(long qqId) {
        return submitOnce(qqId, () -> syncNow(qqId));
    }

    private SyncResult syncNow(long qqId) {
        MaimaiProviderType provider = preferenceService.getProvider(qqId);
        requireTargetBinding(qqId, provider);

        String arcadeCredential = maimaiBindingService.credential(qqId);
        List<JsonNode> arcadeScores = adapterClient.arcadeScores(
                arcadeCredential
        );
        int changed = switch (provider) {
            case LXNS -> synchronizeLxns(qqId, arcadeScores);
            case DIVING_FISH -> synchronizeDivingFish(qqId, arcadeScores);
        };
        return new SyncResult(arcadeScores.size(), provider, changed);
    }

    private void requireTargetBinding(
            long qqId,
            MaimaiProviderType provider
    ) {
        if (provider == MaimaiProviderType.LXNS
                && !lxnsBindingService.status(qqId).bound()) {
            throw new IllegalStateException(
                    "当前数据源是落雪，但尚未绑定落雪，请先私聊发送 /bind lxns"
            );
        }
        if (provider == MaimaiProviderType.DIVING_FISH
                && !divingFishBindingService.isBound(qqId)) {
            throw new IllegalStateException(
                    "当前数据源是水鱼，但尚未绑定水鱼，请先私聊发送 /bind fish"
            );
        }
    }

    private int synchronizeLxns(
            long qqId,
            List<JsonNode> sourceScores
    ) {
        try {
            String accessToken = lxnsBindingService.validAccessToken(qqId);
            List<JsonNode> target = lxnsScoreClient.scores(accessToken);
            List<JsonNode> changed = changedScores(sourceScores, target);
            lxnsScoreClient.upload(accessToken, changed);
            return changed.size();
        } catch (RuntimeException error) {
            throw new MaimaiSyncTargetException(
                    MaimaiProviderType.LXNS,
                    error
            );
        }
    }

    private int synchronizeDivingFish(
            long qqId,
            List<JsonNode> sourceScores
    ) {
        try {
            String importToken = divingFishBindingService.importToken(qqId);
            List<JsonNode> target = adapterClient.divingFishScores(importToken);
            List<JsonNode> changed = changedScores(sourceScores, target);
            adapterClient.uploadDivingFishScores(importToken, changed);
            return changed.size();
        } catch (RuntimeException error) {
            throw new MaimaiSyncTargetException(
                    MaimaiProviderType.DIVING_FISH,
                    error
            );
        }
    }

    private <T> CompletableFuture<T> submitOnce(
            long qqId,
            Supplier<T> task
    ) {
        if (!activeUsers.add(qqId)) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("已有更新任务正在执行，请勿重复提交。")
            );
        }
        return CompletableFuture.supplyAsync(task, syncExecutor)
                .whenComplete((result, error) -> activeUsers.remove(qqId));
    }

    static List<JsonNode> changedScores(
            List<JsonNode> source,
            List<JsonNode> target
    ) {
        Map<ScoreKey, JsonNode> targetByKey = new HashMap<>();
        for (JsonNode score : target) {
            targetByKey.put(ScoreKey.from(score), score);
        }

        List<JsonNode> changed = new ArrayList<>();
        for (JsonNode sourceScore : source) {
            JsonNode targetScore = targetByKey.get(ScoreKey.from(sourceScore));
            if (targetScore == null) {
                changed.add(sourceScore.deepCopy());
                continue;
            }
            if (!hasImprovement(sourceScore, targetScore)) {
                continue;
            }
            changed.add(mergeBest(sourceScore, targetScore));
        }
        return changed;
    }

    private static boolean hasImprovement(
            JsonNode source,
            JsonNode target
    ) {
        return decimal(source, "achievements")
                > decimal(target, "achievements") + ACHIEVEMENT_EPSILON
                || integer(source, "dx_score") > integer(target, "dx_score")
                || fcRank(source.get("fc")) > fcRank(target.get("fc"))
                || fsRank(source.get("fs")) > fsRank(target.get("fs"));
    }

    private static JsonNode mergeBest(JsonNode source, JsonNode target) {
        ObjectNode merged = source.deepCopy();
        putMaximumDecimal(merged, target, "achievements");
        putMaximumInteger(merged, target, "dx_score");
        putBetterBadge(merged, target, "fc", true);
        putBetterBadge(merged, target, "fs", false);
        return merged;
    }

    private static void putBetterBadge(
            ObjectNode merged,
            JsonNode target,
            String field,
            boolean fc
    ) {
        JsonNode current = merged.get(field);
        JsonNode existing = target.get(field);
        int currentRank = fc ? fcRank(current) : fsRank(current);
        int existingRank = fc ? fcRank(existing) : fsRank(existing);
        if (existingRank > currentRank) {
            merged.set(field, existing.deepCopy());
        }
    }

    private static int fcRank(JsonNode value) {
        if (value == null || value.isNull()) {
            return 0;
        }
        if (value.isIntegralNumber()) {
            return switch (value.asInt()) {
                case 3 -> 1;
                case 2 -> 2;
                case 1 -> 3;
                case 0 -> 4;
                default -> 0;
            };
        }
        return switch (value.asText().toLowerCase()) {
            case "fc" -> 1;
            case "fcp" -> 2;
            case "ap" -> 3;
            case "app" -> 4;
            default -> 0;
        };
    }

    private static int fsRank(JsonNode value) {
        if (value == null || value.isNull()) {
            return 0;
        }
        if (value.isIntegralNumber()) {
            int rank = value.asInt() + 1;
            return rank >= 1 && rank <= 5 ? rank : 0;
        }
        return switch (value.asText().toLowerCase()) {
            case "sync" -> 1;
            case "fs" -> 2;
            case "fsp" -> 3;
            case "fsd", "fdx" -> 4;
            case "fsdp", "fdxp" -> 5;
            default -> 0;
        };
    }

    private static void putMaximumDecimal(
            ObjectNode merged,
            JsonNode other,
            String field
    ) {
        double value = Math.max(
                decimal(merged, field),
                decimal(other, field)
        );
        merged.put(field, value);
    }

    private static void putMaximumInteger(
            ObjectNode merged,
            JsonNode other,
            String field
    ) {
        int value = Math.max(integer(merged, field), integer(other, field));
        if (value < 0) {
            merged.putNull(field);
        } else {
            merged.put(field, value);
        }
    }

    private static double decimal(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.asDouble() : -1D;
    }

    private static int integer(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isInt() || value.isLong() ? value.asInt() : -1;
    }

    private record ScoreKey(int id, String type, int levelIndex) {

        private static ScoreKey from(JsonNode score) {
            return new ScoreKey(
                    score.path("id").asInt(),
                    score.path("type").asText(),
                    score.path("level_index").asInt()
            );
        }
    }
}
