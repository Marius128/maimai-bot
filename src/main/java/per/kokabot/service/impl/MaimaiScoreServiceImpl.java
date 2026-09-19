package per.kokabot.service.impl;

import org.springframework.stereotype.Service;
import per.kokabot.maimai.model.B50Result;
import per.kokabot.provider.MaimaiProviderRouter;
import per.kokabot.provider.MaimaiProviderType;
import per.kokabot.service.MaimaiProviderPreferenceService;
import per.kokabot.service.MaimaiScoreService;

import java.util.concurrent.CompletableFuture;

@Service
public class MaimaiScoreServiceImpl implements MaimaiScoreService {

    private final MaimaiProviderPreferenceService preferenceService;
    private final MaimaiProviderRouter providerRouter;

    public MaimaiScoreServiceImpl(
            MaimaiProviderPreferenceService preferenceService,
            MaimaiProviderRouter providerRouter
    ) {
        this.preferenceService = preferenceService;
        this.providerRouter = providerRouter;
    }

    @Override
    public CompletableFuture<B50Result> queryB50(long qqId) {
        try {
            MaimaiProviderType providerType =
                    preferenceService.getProvider(qqId);

            return providerRouter.getProvider(providerType)
                    .queryB50(qqId);
        } catch (Exception error) {
            return CompletableFuture.failedFuture(error);
        }
    }
}
