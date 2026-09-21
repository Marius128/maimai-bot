package per.kokabot.service.impl;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import per.kokabot.maimai.model.B50Result;
import per.kokabot.maimai.model.ScoreRecord;
import per.kokabot.provider.divingfish.oauth.DivingFishOAuthClient;
import per.kokabot.provider.divingfish.oauth.DivingFishOAuthException;
import per.kokabot.provider.divingfish.response.DivingFishB50ResponseVO;
import per.kokabot.provider.divingfish.response.DivingFishRecordsResponseVO;
import per.kokabot.service.DivingFishOAuthService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

@Service
public class DivingFishOAuthServiceImpl implements DivingFishOAuthService {

    private static final int TOKEN_EXPIRY_MARGIN_SECONDS = 30;

    private final DivingFishOAuthClient client;
    private final Executor executor;
    private final ConcurrentHashMap<Long, CachedToken> tokenCache =
            new ConcurrentHashMap<>();

    public DivingFishOAuthServiceImpl(
            DivingFishOAuthClient client,
            @Qualifier("maimaiSyncExecutor") Executor executor
    ) {
        this.client = client;
        this.executor = executor;
    }

    @Override
    public boolean isConfigured() {
        return client.isConfigured();
    }

    @Override
    public AuthorizationLink beginAuthorization(long qqId) {
        String qq = Long.toString(qqId);
        DivingFishOAuthClient.DeviceAuthorization authorization =
                client.beginAuthorization(
                        subjectRef(qq),
                        maskQq(qq)
                );
        return new AuthorizationLink(
                authorization.url(),
                authorization.expiresIn()
        );
    }

    @Override
    public CompletableFuture<Boolean> isAuthorized(long qqId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                accessToken(qqId);
                return true;
            } catch (DivingFishOAuthException error) {
                if (error.consentRequired()) {
                    return false;
                }
                throw error;
            }
        }, executor);
    }

    @Override
    public CompletableFuture<B50Result> queryB50(long qqId) {
        return CompletableFuture.supplyAsync(() -> queryB50Now(qqId), executor);
    }

    @Override
    public void clearCachedToken(long qqId) {
        tokenCache.remove(qqId);
    }

    private B50Result queryB50Now(long qqId) {
        String accessToken = accessToken(qqId);
        try {
            DivingFishRecordsResponseVO oldRecords =
                    client.records(accessToken, false);
            DivingFishRecordsResponseVO newRecords =
                    client.records(accessToken, true);
            List<ScoreRecord> best35 = best(oldRecords.records(), 35);
            List<ScoreRecord> best15 = best(newRecords.records(), 15);
            String nickname = hasText(newRecords.nickname())
                    ? newRecords.nickname()
                    : oldRecords.nickname();
            int rating = newRecords.rating() > 0
                    ? newRecords.rating()
                    : oldRecords.rating();
            return new B50Result(nickname, rating, best35, best15);
        } catch (DivingFishOAuthException error) {
            if (error.statusCode() == 401) {
                tokenCache.remove(qqId);
            }
            throw error;
        }
    }

    private List<ScoreRecord> best(
            List<DivingFishB50ResponseVO.Score> scores,
            int limit
    ) {
        if (scores == null) {
            return List.of();
        }
        return scores.stream()
                .sorted(Comparator
                        .comparingInt(DivingFishB50ResponseVO.Score::ra)
                        .reversed()
                        .thenComparing(
                                Comparator.comparingDouble(
                                        DivingFishB50ResponseVO.Score::achievements
                                ).reversed()
                        ))
                .limit(limit)
                .map(this::toScoreRecord)
                .toList();
    }

    private ScoreRecord toScoreRecord(DivingFishB50ResponseVO.Score score) {
        return new ScoreRecord(
                score.songId(),
                score.title(),
                score.type(),
                score.levelLabel(),
                score.level(),
                score.ds(),
                score.achievements(),
                score.dxScore(),
                score.ra(),
                0,
                score.fc(),
                score.fs()
        );
    }

    private synchronized String accessToken(long qqId) {
        CachedToken cached = tokenCache.get(qqId);
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
            return cached.value();
        }
        DivingFishOAuthClient.AccessToken token = client.exchangeToken(
                subjectRef(Long.toString(qqId))
        );
        long usableSeconds = Math.max(
                1,
                token.expiresIn() - TOKEN_EXPIRY_MARGIN_SECONDS
        );
        tokenCache.put(
                qqId,
                new CachedToken(
                        token.value(),
                        Instant.now().plusSeconds(usableSeconds)
                )
        );
        return token.value();
    }

    private String subjectRef(String externalId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(
                    (client.clientId() + ":" + externalId)
                            .getBytes(StandardCharsets.UTF_8)
            );
            return java.util.HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("当前环境不支持 SHA-256", impossible);
        }
    }

    private String maskQq(String qq) {
        if (qq.length() <= 4) {
            return "QQ ****";
        }
        return "QQ " + qq.substring(0, 2) + "****"
                + qq.substring(qq.length() - 2);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record CachedToken(String value, Instant expiresAt) {
    }
}
