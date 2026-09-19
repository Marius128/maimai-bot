package per.kokabot.service.impl;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import per.kokabot.entity.LxnsOAuthBindingDO;
import per.kokabot.mapper.LxnsOAuthBindingMapper;
import per.kokabot.provider.lxns.oauth.LxnsOAuthClient;
import per.kokabot.security.TokenCipher;
import per.kokabot.service.LxnsOAuthBindingService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

@Service
public class LxnsOAuthBindingServiceImpl
        implements LxnsOAuthBindingService {

    private static final Duration AUTHORIZATION_LIFETIME = Duration.ofMinutes(10);
    private static final Duration REFRESH_TOKEN_LIFETIME = Duration.ofDays(30);

    private final LxnsOAuthClient oauthClient;
    private final LxnsOAuthBindingMapper bindingMapper;
    private final TokenCipher tokenCipher;
    private final Executor oauthExecutor;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<Long, PendingAuthorization> pendingAuthorizations =
            new ConcurrentHashMap<>();
    private final Map<Long, Object> refreshLocks = new ConcurrentHashMap<>();

    public LxnsOAuthBindingServiceImpl(
            LxnsOAuthClient oauthClient,
            LxnsOAuthBindingMapper bindingMapper,
            TokenCipher tokenCipher,
            @Qualifier("oauthExecutor") Executor oauthExecutor
    ) {
        this.oauthClient = oauthClient;
        this.bindingMapper = bindingMapper;
        this.tokenCipher = tokenCipher;
        this.oauthExecutor = oauthExecutor;
    }

    @Override
    public String beginAuthorization(long qqId) {
        requireConfigured();
        String verifier = randomUrlSafe(64);
        String challenge = sha256UrlSafe(verifier);
        pendingAuthorizations.put(
                qqId,
                new PendingAuthorization(
                        verifier,
                        Instant.now().plus(AUTHORIZATION_LIFETIME)
                )
        );
        removeExpiredAuthorizations();
        return oauthClient.authorizationUrl(challenge);
    }

    @Override
    public CompletableFuture<BindingResult> completeAuthorization(
            long qqId,
            String authorizationCode
    ) {
        String normalizedCode = authorizationCode == null
                ? ""
                : authorizationCode.trim();
        if (normalizedCode.isBlank()) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("授权码不能为空")
            );
        }

        PendingAuthorization pending = pendingAuthorizations.get(qqId);
        if (pending == null || pending.expiresAt().isBefore(Instant.now())) {
            pendingAuthorizations.remove(qqId);
            return CompletableFuture.failedFuture(
                    new IllegalStateException(
                            "授权会话已过期，请重新执行 /bind lxns"
                    )
            );
        }

        return CompletableFuture.supplyAsync(() -> {
            requireConfigured();
            LxnsOAuthClient.TokenResponse tokens =
                    oauthClient.exchangeAuthorizationCode(
                            normalizedCode,
                            pending.codeVerifier()
                    );
            requireScopes(tokens.scope());
            LxnsOAuthClient.UserInfo userInfo =
                    oauthClient.userInfo(tokens.accessToken());

            Instant now = Instant.now();
            LxnsOAuthBindingDO binding = new LxnsOAuthBindingDO();
            binding.setQqId(qqId);
            binding.setSubject(userInfo.subject());
            binding.setUsername(userInfo.username());
            binding.setAccessTokenEncrypted(
                    tokenCipher.encrypt(tokens.accessToken())
            );
            binding.setRefreshTokenEncrypted(
                    tokenCipher.encrypt(tokens.refreshToken())
            );
            binding.setAccessTokenExpiresAt(Timestamp.from(
                    now.plusSeconds(Math.max(tokens.expiresIn(), 1))
            ));
            binding.setRefreshTokenExpiresAt(Timestamp.from(
                    now.plus(REFRESH_TOKEN_LIFETIME)
            ));
            binding.setScope(tokens.scope());
            bindingMapper.upsert(binding);
            pendingAuthorizations.remove(qqId, pending);
            return new BindingResult(
                    displayUsername(userInfo),
                    tokens.scope()
            );
        }, oauthExecutor);
    }

    @Override
    public BindingStatus status(long qqId) {
        LxnsOAuthBindingDO binding = bindingMapper.selectByQqId(qqId);
        if (binding == null) {
            return new BindingStatus(false, "");
        }
        return new BindingStatus(
                true,
                binding.getUsername() == null ? "" : binding.getUsername()
        );
    }

    @Override
    public String validAccessToken(long qqId) {
        requireConfigured();
        LxnsOAuthBindingDO binding = requireBinding(qqId);
        if (isAccessTokenUsable(binding)) {
            return tokenCipher.decrypt(binding.getAccessTokenEncrypted());
        }

        Object lock = refreshLocks.computeIfAbsent(qqId, ignored -> new Object());
        synchronized (lock) {
            binding = requireBinding(qqId);
            if (isAccessTokenUsable(binding)) {
                return tokenCipher.decrypt(binding.getAccessTokenEncrypted());
            }
            if (binding.getRefreshTokenExpiresAt() == null
                    || !binding.getRefreshTokenExpiresAt()
                    .toInstant().isAfter(Instant.now())) {
                throw new IllegalStateException(
                        "落雪授权已过期，请重新私聊发送 /bind lxns"
                );
            }

            String refreshToken = tokenCipher.decrypt(
                    binding.getRefreshTokenEncrypted()
            );
            LxnsOAuthClient.TokenResponse tokens = oauthClient.refresh(
                    refreshToken
            );
            String scope = tokens.scope() == null || tokens.scope().isBlank()
                    ? binding.getScope()
                    : tokens.scope();
            requireScopes(scope);

            Instant now = Instant.now();
            binding.setAccessTokenEncrypted(
                    tokenCipher.encrypt(tokens.accessToken())
            );
            binding.setRefreshTokenEncrypted(
                    tokenCipher.encrypt(tokens.refreshToken())
            );
            binding.setAccessTokenExpiresAt(Timestamp.from(
                    now.plusSeconds(Math.max(tokens.expiresIn(), 1))
            ));
            binding.setRefreshTokenExpiresAt(Timestamp.from(
                    now.plus(REFRESH_TOKEN_LIFETIME)
            ));
            binding.setScope(scope);
            bindingMapper.upsert(binding);
            return tokens.accessToken();
        }
    }

    @Override
    public boolean unbind(long qqId) {
        pendingAuthorizations.remove(qqId);
        refreshLocks.remove(qqId);
        return bindingMapper.deleteByQqId(qqId) > 0;
    }

    private LxnsOAuthBindingDO requireBinding(long qqId) {
        LxnsOAuthBindingDO binding = bindingMapper.selectByQqId(qqId);
        if (binding == null) {
            throw new IllegalStateException(
                    "尚未绑定落雪，请先私聊发送 /bind lxns"
            );
        }
        return binding;
    }

    private boolean isAccessTokenUsable(LxnsOAuthBindingDO binding) {
        return binding.getAccessTokenExpiresAt() != null
                && binding.getAccessTokenExpiresAt().toInstant()
                .isAfter(Instant.now().plusSeconds(60));
    }

    private void requireConfigured() {
        if (!oauthClient.isConfigured()) {
            throw new IllegalStateException(
                    "管理员尚未配置落雪 OAuth 应用"
            );
        }
        if (!tokenCipher.isConfigured()) {
            throw new IllegalStateException(
                    "管理员尚未配置 TOKEN_ENCRYPTION_KEY"
            );
        }
    }

    private void requireScopes(String scope) {
        String normalized = scope == null ? "" : " " + scope + " ";
        if (!normalized.contains(" read_player ")
                || !normalized.contains(" write_player ")
                || !normalized.contains(" openid ")) {
            throw new IllegalStateException(
                    "落雪授权缺少 read_player、write_player 或 openid 权限"
            );
        }
    }

    private String displayUsername(LxnsOAuthClient.UserInfo userInfo) {
        return userInfo.username().isBlank()
                ? userInfo.subject()
                : userInfo.username();
    }

    private void removeExpiredAuthorizations() {
        Instant now = Instant.now();
        pendingAuthorizations.entrySet().removeIf(
                entry -> entry.getValue().expiresAt().isBefore(now)
        );
    }

    private String randomUrlSafe(int byteCount) {
        byte[] bytes = new byte[byteCount];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256UrlSafe(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(digest);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", error);
        }
    }

    private record PendingAuthorization(
            String codeVerifier,
            Instant expiresAt
    ) {
    }
}
