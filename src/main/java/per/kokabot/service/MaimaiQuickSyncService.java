package per.kokabot.service;

import java.util.concurrent.CompletableFuture;

public interface MaimaiQuickSyncService {

    CompletableFuture<SyncResult> sync(long qqId);

    record SyncResult(int fetched, int changed) {
    }
}
