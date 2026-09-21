package per.kokabot.service;

import per.kokabot.maimai.model.B50Result;

import java.util.concurrent.CompletableFuture;

public interface DivingFishOAuthService {

    boolean isConfigured();

    AuthorizationLink beginAuthorization(long qqId);

    CompletableFuture<Boolean> isAuthorized(long qqId);

    CompletableFuture<B50Result> queryB50(long qqId);

    void clearCachedToken(long qqId);

    record AuthorizationLink(String url, int expiresIn) {
    }
}
