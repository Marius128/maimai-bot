package per.kokabot.provider.lxns.oauth;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class LxnsOAuthClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String clientId;
    private final String clientSecret;
    private final String authorizeUrl;
    private final String tokenUrl;
    private final String userinfoUrl;
    private final String redirectUri;
    private final String scopes;

    public LxnsOAuthClient(
            ObjectMapper objectMapper,
            @Value("${maimai.provider.lxns.oauth.client-id:}") String clientId,
            @Value("${maimai.provider.lxns.oauth.client-secret:}") String clientSecret,
            @Value("${maimai.provider.lxns.oauth.authorize-url}") String authorizeUrl,
            @Value("${maimai.provider.lxns.oauth.token-url}") String tokenUrl,
            @Value("${maimai.provider.lxns.oauth.userinfo-url}") String userinfoUrl,
            @Value("${maimai.provider.lxns.oauth.redirect-uri}") String redirectUri,
            @Value("${maimai.provider.lxns.oauth.scopes}") String scopes
    ) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .build();
        this.clientId = clientId.trim();
        this.clientSecret = clientSecret.trim();
        this.authorizeUrl = authorizeUrl.trim();
        this.tokenUrl = tokenUrl.trim();
        this.userinfoUrl = userinfoUrl.trim();
        this.redirectUri = redirectUri.trim();
        this.scopes = scopes.trim();
    }

    public boolean isConfigured() {
        return !clientId.isBlank() && !clientSecret.isBlank();
    }

    public String authorizationUrl(String codeChallenge) {
        requireConfigured();
        Map<String, String> query = new LinkedHashMap<>();
        query.put("response_type", "code");
        query.put("client_id", clientId);
        query.put("redirect_uri", redirectUri);
        query.put("scope", scopes);
        query.put("code_challenge", codeChallenge);
        query.put("code_challenge_method", "S256");
        return authorizeUrl + "?" + formEncode(query);
    }

    public TokenResponse exchangeAuthorizationCode(
            String code,
            String codeVerifier
    ) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("client_id", clientId);
        body.put("client_secret", clientSecret);
        body.put("grant_type", "authorization_code");
        body.put("code", code);
        body.put("redirect_uri", redirectUri);
        body.put("code_verifier", codeVerifier);
        return requestToken(body);
    }

    public TokenResponse refresh(String refreshToken) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("client_id", clientId);
        body.put("client_secret", clientSecret);
        body.put("grant_type", "refresh_token");
        body.put("refresh_token", refreshToken);
        return requestToken(body);
    }

    public UserInfo userInfo(String accessToken) {
        requireConfigured();
        HttpRequest request = HttpRequest.newBuilder(URI.create(userinfoUrl))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();
        JsonNode json = send(request);
        String subject = json.path("sub").asText("").trim();
        if (subject.isBlank()) {
            throw new LxnsOAuthException(502, "落雪未返回用户标识");
        }
        String username = json.path("preferred_username").asText("").trim();
        if (username.isBlank()) {
            username = json.path("name").asText("").trim();
        }
        return new UserInfo(subject, username);
    }

    private TokenResponse requestToken(Map<String, String> body) {
        requireConfigured();
        HttpRequest request = HttpRequest.newBuilder(URI.create(tokenUrl))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formEncode(body)))
                .build();
        JsonNode json = send(request);
        try {
            TokenResponse response = objectMapper.treeToValue(
                    json,
                    TokenResponse.class
            );
            if (response.accessToken() == null
                    || response.accessToken().isBlank()
                    || response.refreshToken() == null
                    || response.refreshToken().isBlank()) {
                throw new LxnsOAuthException(502, "落雪返回的 Token 不完整");
            }
            return response;
        } catch (LxnsOAuthException error) {
            throw error;
        } catch (Exception error) {
            throw new LxnsOAuthException("无法解析落雪 Token 响应", error);
        }
    }

    private JsonNode send(HttpRequest request) {
        try {
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            JsonNode json = objectMapper.readTree(response.body());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String message = json.path("error_description").asText("");
                if (message.isBlank()) {
                    message = json.path("message").asText("落雪 OAuth 请求失败");
                }
                throw new LxnsOAuthException(response.statusCode(), message);
            }
            return json;
        } catch (LxnsOAuthException error) {
            throw error;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new LxnsOAuthException("请求落雪 OAuth 服务失败", error);
        } catch (IOException error) {
            throw new LxnsOAuthException("请求落雪 OAuth 服务失败", error);
        }
    }

    private String formEncode(Map<String, String> values) {
        return values.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new IllegalStateException(
                    "管理员尚未配置落雪 OAuth 应用 ID 或应用密钥"
            );
        }
    }

    public record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("token_type") String tokenType,
            @JsonProperty("expires_in") long expiresIn,
            @JsonProperty("refresh_token") String refreshToken,
            String scope,
            @JsonProperty("id_token") String idToken
    ) {
    }

    public record UserInfo(String subject, String username) {
    }
}
