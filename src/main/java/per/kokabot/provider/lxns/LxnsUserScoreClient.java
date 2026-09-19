package per.kokabot.provider.lxns;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
public class LxnsUserScoreClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final URI scoresUri;

    public LxnsUserScoreClient(
            ObjectMapper objectMapper,
            @Value("${maimai.provider.lxns.base-url}") String baseUrl
    ) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();
        this.scoresUri = URI.create(
                baseUrl.replaceAll("/+$", "")
                        + "/user/maimai/player/scores"
        );
    }

    public List<JsonNode> scores(String accessToken) {
        HttpRequest request = request(accessToken)
                .GET()
                .build();
        JsonNode data = send(request);
        if (!data.isArray()) {
            throw new IllegalStateException("落雪返回的成绩格式无效");
        }
        List<JsonNode> scores = new ArrayList<>(data.size());
        data.forEach(scores::add);
        return scores;
    }

    public void upload(String accessToken, List<JsonNode> scores) {
        if (scores.isEmpty()) {
            return;
        }
        ArrayNode values = objectMapper.createArrayNode();
        scores.forEach(values::add);
        ObjectNode body = objectMapper.createObjectNode();
        body.set("scores", values);

        HttpRequest request = request(accessToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        send(request);
    }

    private HttpRequest.Builder request(String accessToken) {
        return HttpRequest.newBuilder(scoresUri)
                .timeout(TIMEOUT)
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + accessToken);
    }

    private JsonNode send(HttpRequest request) {
        try {
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            JsonNode root = objectMapper.readTree(response.body());
            boolean success = root.path("success").asBoolean(
                    response.statusCode() >= 200 && response.statusCode() < 300
            );
            if (response.statusCode() < 200
                    || response.statusCode() >= 300
                    || !success) {
                String message = root.path("message").asText("");
                int code = response.statusCode() >= 400
                        ? response.statusCode()
                        : root.path("code").asInt(response.statusCode());
                throw new LxnsApiException(code, message);
            }
            return root.has("data") ? root.path("data") : root;
        } catch (LxnsApiException error) {
            throw error;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("落雪成绩请求被中断", error);
        } catch (IOException error) {
            throw new IllegalStateException("无法请求落雪成绩接口", error);
        }
    }
}
