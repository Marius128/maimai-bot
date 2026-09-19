package per.kokabot.maimai.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
public class MaimaiAdapterClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String baseUrl;

    public MaimaiAdapterClient(
            ObjectMapper objectMapper,
            @Value("${maimai.adapter.base-url}") String baseUrl
    ) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    public String resolveArcadeCredential(String sgid) {
        String url = baseUrl + "/arcade/identifiers?code="
                + URLEncoder.encode(sgid, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .GET()
                .build();
        JsonNode json = send(request);
        String credential = json.path("credentials").asText("").trim();
        if (credential.isBlank()) {
            throw new MaimaiAdapterException(
                    502,
                    "舞萌适配器未返回玩家 UID"
            );
        }
        return credential;
    }

    public List<JsonNode> arcadeScores(String credential) {
        return scores("/arcade/scores", credential);
    }

    private List<JsonNode> scores(String path, String credential) {
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create(credentialUrl(path, credential))
                )
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .GET()
                .build();
        JsonNode json = send(request);
        if (!json.isArray()) {
            throw new MaimaiAdapterException(
                    502,
                    "舞萌适配器返回的成绩格式无效"
            );
        }
        List<JsonNode> result = new ArrayList<>(json.size());
        json.forEach(result::add);
        return result;
    }

    private String credentialUrl(String path, String credential) {
        return baseUrl + path + "?credentials="
                + URLEncoder.encode(credential, StandardCharsets.UTF_8);
    }

    private JsonNode send(HttpRequest request) {
        try {
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            String responseBody = response.body();
            JsonNode json = responseBody == null || responseBody.isBlank()
                    ? NullNode.getInstance()
                    : objectMapper.readTree(responseBody);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String message = json.path("message").asText("");
                if (message.isBlank()) {
                    message = json.path("detail").asText("");
                }
                if (message.isBlank()) {
                    message = "舞萌适配器请求失败";
                }
                throw new MaimaiAdapterException(response.statusCode(), message);
            }
            return json;
        } catch (MaimaiAdapterException error) {
            throw error;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new MaimaiAdapterException("舞萌适配器请求被中断", error);
        } catch (IOException error) {
            throw new MaimaiAdapterException("无法连接舞萌适配器", error);
        }
    }
}
