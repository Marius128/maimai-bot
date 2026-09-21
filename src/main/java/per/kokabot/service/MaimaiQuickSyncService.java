package per.kokabot.service;

import per.kokabot.provider.MaimaiProviderType;

import java.util.concurrent.CompletableFuture;

public interface MaimaiQuickSyncService {

    CompletableFuture<SyncResult> sync(long qqId);

    record SyncResult(
            int fetched,
            MaimaiProviderType provider,
            int changed
    ) {
    }
}
