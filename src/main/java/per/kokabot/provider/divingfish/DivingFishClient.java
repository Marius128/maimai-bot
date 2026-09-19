package per.kokabot.provider.divingfish;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import per.kokabot.provider.divingfish.request.DivingFishB50Request;
import per.kokabot.provider.divingfish.response.DivingFishB50ResponseVO;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * @BelongsProject: maimai-bot
 * @BelongsPackage: per.kokabot.provider.divingfish
 * @Author: L
 * @CreateTime: 2026-09-19 09:45
 * @Description: TODO
 * @Version: 1.0
 */
@Component
public class DivingFishClient {
    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final URI queryPlayerUri;

    public DivingFishClient(
            ObjectMapper objectMapper,
            @Value("${maimai.provider.diving-fish.base-url}") String baseUrl
    ) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();

        String normalizedBaseUrl = baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1)
                : baseUrl;

        this.queryPlayerUri = URI.create(
                normalizedBaseUrl + "/query/player"
        );
    }

    public CompletableFuture<DivingFishB50ResponseVO> queryB50(long qqId) {
        final String requestBody;

        try {
            DivingFishB50Request request =
                    DivingFishB50Request.of(qqId);

            requestBody = objectMapper.writeValueAsString(request);
        } catch (Exception error) {
            return CompletableFuture.failedFuture(error);
        }

        HttpRequest request = HttpRequest.newBuilder(queryPlayerUri)
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        requestBody,
                        StandardCharsets.UTF_8
                ))
                .build();

        return httpClient.sendAsync(
            request,
            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        ).thenApply(response -> {
            if (response.statusCode() != 200) {
                throw new DivingFishApiException(response.statusCode());
            }

            try {
                return objectMapper.readValue(
                        response.body(),
                        DivingFishB50ResponseVO.class
                );
            } catch (Exception error) {
                throw new IllegalStateException(
                        "无法解析水鱼接口响应",
                        error
                );
            }
        });
    }
}
