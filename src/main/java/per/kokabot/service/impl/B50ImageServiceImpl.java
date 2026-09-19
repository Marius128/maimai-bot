package per.kokabot.service.impl;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import per.kokabot.maimai.asset.MaimaiAssetService;
import per.kokabot.maimai.image.B50ImageRenderer;
import per.kokabot.maimai.model.B50Result;
import per.kokabot.service.B50ImageService;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
public class B50ImageServiceImpl implements B50ImageService {

    private final B50ImageRenderer renderer;
    private final MaimaiAssetService assetService;
    private final Executor renderExecutor;

    public B50ImageServiceImpl(
            B50ImageRenderer renderer,
            MaimaiAssetService assetService,
            @Qualifier("b50RenderExecutor") Executor renderExecutor
    ) {
        this.renderer = renderer;
        this.assetService = assetService;
        this.renderExecutor = renderExecutor;
    }

    @Override
    public CompletableFuture<byte[]> render(B50Result result) {
        return assetService.enrichScoreMetadata(result)
                .thenCompose(enriched ->
                        assetService.loadJacketDataUris(enriched)
                                .thenCompose(jackets ->
                                        CompletableFuture.supplyAsync(
                                                () -> renderer.render(
                                                        enriched,
                                                        jackets
                                                ),
                                                renderExecutor
                                        )
                                )
                );
    }
}
