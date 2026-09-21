package per.kokabot.service.impl;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import per.kokabot.entity.DivingFishBindingDO;
import per.kokabot.maimai.adapter.MaimaiAdapterClient;
import per.kokabot.mapper.DivingFishBindingMapper;
import per.kokabot.security.TokenCipher;
import per.kokabot.service.DivingFishBindingService;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
public class DivingFishBindingServiceImpl
        implements DivingFishBindingService {

    private static final int MAX_TOKEN_LENGTH = 2048;

    private final MaimaiAdapterClient adapterClient;
    private final DivingFishBindingMapper bindingMapper;
    private final TokenCipher tokenCipher;
    private final Executor syncExecutor;

    public DivingFishBindingServiceImpl(
            MaimaiAdapterClient adapterClient,
            DivingFishBindingMapper bindingMapper,
            TokenCipher tokenCipher,
            @Qualifier("maimaiSyncExecutor") Executor syncExecutor
    ) {
        this.adapterClient = adapterClient;
        this.bindingMapper = bindingMapper;
        this.tokenCipher = tokenCipher;
        this.syncExecutor = syncExecutor;
    }

    @Override
    public CompletableFuture<Void> bind(long qqId, String importToken) {
        String normalized = importToken == null ? "" : importToken.trim();
        if (normalized.isBlank() || normalized.length() > MAX_TOKEN_LENGTH
                || normalized.chars().anyMatch(Character::isWhitespace)) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("水鱼 Import-Token 格式无效")
            );
        }
        if (!tokenCipher.isConfigured()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException(
                            "管理员尚未配置 TOKEN_ENCRYPTION_KEY"
                    )
            );
        }

        return CompletableFuture.runAsync(() -> {
            adapterClient.divingFishScores(normalized);
            DivingFishBindingDO binding = new DivingFishBindingDO();
            binding.setQqId(qqId);
            binding.setImportTokenEncrypted(tokenCipher.encrypt(normalized));
            bindingMapper.upsert(binding);
        }, syncExecutor);
    }

    @Override
    public boolean isBound(long qqId) {
        return bindingMapper.selectByQqId(qqId) != null;
    }

    @Override
    public String importToken(long qqId) {
        DivingFishBindingDO binding = bindingMapper.selectByQqId(qqId);
        if (binding == null) {
            throw new IllegalStateException(
                    "尚未绑定水鱼，请先私聊发送 /bind fish"
            );
        }
        if (!tokenCipher.isConfigured()) {
            throw new IllegalStateException(
                    "管理员尚未配置 TOKEN_ENCRYPTION_KEY"
            );
        }
        return tokenCipher.decrypt(binding.getImportTokenEncrypted());
    }

    @Override
    public boolean unbind(long qqId) {
        return bindingMapper.deleteByQqId(qqId) > 0;
    }
}
