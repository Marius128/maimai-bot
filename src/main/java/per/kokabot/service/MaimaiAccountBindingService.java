package per.kokabot.service;

import java.util.concurrent.CompletableFuture;

public interface MaimaiAccountBindingService {

    CompletableFuture<Void> bind(long qqId, String sgid);

    boolean isBound(long qqId);

    String credential(long qqId);

    boolean unbind(long qqId);
}
