package per.kokabot.provider.divingfish.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import per.kokabot.provider.divingfish.response.DivingFishRecordsResponseVO;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class DivingFishOAuthClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private static final String READ_SCOPE = "prober.records.read";
    private static final String ON_BEHALF_OF_GRANT =
            "urn:diving-fish:params:oauth:grant-type:on-behalf-of";

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String authBaseUrl;
    private final String proberBaseUrl;
    private final String clientId;
    private final String clientSecret;

    public DivingFishOAuthClient(
            ObjectMapper objectMapper,
            @Value("${maimai.provider.diving-fish.base-url}")
            String proberBaseUrl,
            @Value("${maimai.provider.diving-fish.oauth.auth-url}")
            String authBaseUrl,
            @Value("${maimai.provider.diving-fish.oauth.client-id:}")
            String clientId,
            @Value("${maimai.provider.diving-fish.oauth.client-secret:}")
            String clientSecret
    ) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();
        this.authBaseUrl = stripTrailingSlash(authBaseUrl);
        this.proberBaseUrl = stripTrailingSlash(proberBaseUrl);
        this.clientId = clientId.trim();
        this.clientSecret = clientSecret.trim();
    }

    public boolean isConfigured() {
        return !clientId.isBlank() && !clientSecret.isBlank();
    }

    public String clientId() {
        requireConfigured();
        return clientId;
    }

    public DeviceAuthorization beginAuthorization(
            String subjectRef,
            String bindingLabel
    ) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("client_id", clientId);
        form.put("client_secret", clientSecret);
        form.put("scope", READ_SCOPE);
        form.put("subject_ref", subjectRef);
        form.put("binding_label", bindingLabel);
        JsonNode json = postForm("/oauth/device_authorization", form);
        String url = json.path("verification_uri_complete").asText("");
        if (url.isBlank()) {
            throw new DivingFishOAuthException(
                    "水鱼 OAuth 未返回授权链接",
                    null
            );
        }
        return new DeviceAuthorization(
                url,
                json.path("expires_in").asInt(600)
        );
    }

    public AccessToken exchangeToken(String subjectRef) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", ON_BEHALF_OF_GRANT);
        form.put("client_id", clientId);
        form.put("client_secret", clientSecret);
        form.put("subject", "ref:" + subjectRef);
        form.put("scope", READ_SCOPE);
        JsonNode json = postForm("/oauth/token", form);
        String accessToken = json.path("access_token").asText("");
        if (accessToken.isBlank()) {
            throw new DivingFishOAuthException(
                    "水鱼 OAuth 未返回 access_token",
                    null
            );
        }
        return new AccessToken(
                accessToken,
                json.path("expires_in").asInt(300)
        );
    }

    public DivingFishRecordsResponseVO records(
            String accessToken,
            boolean isNew
    ) {
        requireConfigured();
        URI uri = URI.create(
                proberBaseUrl + "/player/records?is_new=" + isNew
        );
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(TIMEOUT)
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();
        JsonNode json = send(request);
        try {
            return objectMapper.treeToValue(
                    json,
                    DivingFishRecordsResponseVO.class
            );
        } catch (Exception error) {
            throw new DivingFishOAuthException(
                    "无法解析水鱼完整成绩响应",
                    error
            );
        }
    }

    private JsonNode postForm(String path, Map<String, String> values) {
        requireConfigured();
        String body = values.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "="
                        + encode(entry.getValue()))
                .reduce((left, right) -> left + "&" + right)
                .orElse("");
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create(authBaseUrl + path)
                )
                .timeout(TIMEOUT)
                .header("Accept", "application/json")
                .header(
                        "Content-Type",
                        "application/x-www-form-urlencoded"
                )
                .POST(HttpRequest.BodyPublishers.ofString(
                        body,
                        StandardCharsets.UTF_8
                ))
                .build();
        return send(request);
    }

    private JsonNode send(HttpRequest request) {
        try {
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            JsonNode json = response.body() == null
                    || response.body().isBlank()
                    ? objectMapper.createObjectNode()
                    : objectMapper.readTree(response.body());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String errorCode = json.path("error").asText("");
                String message = json.path("error_description").asText("");
                if (message.isBlank()) {
                    message = json.path("message").asText("水鱼 OAuth 请求失败");
                }
                throw new DivingFishOAuthException(
                        response.statusCode(),
                        errorCode,
                        message
                );
            }
            return json;
        } catch (DivingFishOAuthException error) {
            throw error;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new DivingFishOAuthException("水鱼 OAuth 请求被中断", error);
        } catch (IOException error) {
            throw new DivingFishOAuthException("无法连接水鱼 OAuth", error);
        }
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new IllegalStateException(
                    "管理员尚未配置水鱼 OAuth client_id/client_secret"
            );
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String stripTrailingSlash(String value) {
        String result = value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    public record DeviceAuthorization(String url, int expiresIn) {
    }

    public record AccessToken(String value, int expiresIn) {
    }
}
