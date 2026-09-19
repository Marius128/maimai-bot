package per.kokabot.service;

import java.util.concurrent.CompletableFuture;

public interface LxnsOAuthBindingService {

    String beginAuthorization(long qqId);

    CompletableFuture<BindingResult> completeAuthorization(
            long qqId,
            String authorizationCode
    );

    BindingStatus status(long qqId);

    String validAccessToken(long qqId);

    boolean unbind(long qqId);

    record BindingResult(String username, String scope) {
    }

    record BindingStatus(boolean bound, String username) {
    }
}
