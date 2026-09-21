package per.kokabot.service;

import java.util.concurrent.CompletableFuture;

public interface DivingFishBindingService {

    CompletableFuture<Void> bind(long qqId, String importToken);

    boolean isBound(long qqId);

    String importToken(long qqId);

    boolean unbind(long qqId);
}
