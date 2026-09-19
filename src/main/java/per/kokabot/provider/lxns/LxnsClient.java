package per.kokabot.provider.lxns;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import per.kokabot.provider.lxns.response.LxnsApiResponseVO;
import per.kokabot.provider.lxns.response.LxnsB50DataVO;
import per.kokabot.provider.lxns.response.LxnsB50ResponseVO;
import per.kokabot.provider.lxns.response.LxnsPlayerVO;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

@Component
public class LxnsClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String baseUrl;
    private final String apiKey;

    public LxnsClient(
            ObjectMapper objectMapper,
            @Value("${maimai.provider.lxns.base-url}") String baseUrl,
            @Value("${maimai.provider.lxns.api-key:}") String apiKey
    ) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();
        this.baseUrl = baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1)
                : baseUrl;
        this.apiKey = apiKey.trim();
    }

    public CompletableFuture<LxnsB50DataVO> queryB50(long qqId) {
        if (apiKey.isBlank()) {
            return CompletableFuture.failedFuture(
                    new LxnsConfigurationException(
                            "LXNS_API_KEY 尚未配置"
                    )
            );
        }

        URI playerUri = URI.create(
                baseUrl + "/maimai/player/qq/" + qqId
        );

        return get(playerUri, LxnsPlayerVO.class)
                .thenCompose(player -> {
                    URI bestsUri = URI.create(
                            baseUrl + "/maimai/player/"
                                    + player.friendCode() + "/bests"
                    );

                    return get(bestsUri, LxnsB50ResponseVO.class)
                            .thenApply(bests ->
                                    new LxnsB50DataVO(player, bests)
                            );
                });
    }

    private <T> CompletableFuture<T> get(URI uri, Class<T> dataType) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(TIMEOUT)
                .header("Accept", "application/json")
                .header("Authorization", apiKey)
                .GET()
                .build();

        return httpClient.sendAsync(
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        ).thenApply(response -> parseResponse(response, dataType));
    }

    private <T> T parseResponse(
            HttpResponse<String> response,
            Class<T> dataType
    ) {
        try {
            JavaType responseType = objectMapper.getTypeFactory()
                    .constructParametricType(
                            LxnsApiResponseVO.class,
                            dataType
                    );

            LxnsApiResponseVO<T> body =
                    objectMapper.readValue(response.body(), responseType);

            if (response.statusCode() < 200
                    || response.statusCode() >= 300
                    || !body.success()
                    || body.data() == null) {
                int statusCode = response.statusCode() >= 400
                        ? response.statusCode()
                        : body.code();
                throw new LxnsApiException(statusCode, body.message());
            }

            return body.data();
        } catch (LxnsApiException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalStateException(
                    "无法解析落雪接口响应",
                    error
            );
        }
    }
}
