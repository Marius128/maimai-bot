package per.kokabot.service.impl;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import per.kokabot.entity.MaimaiAccountBindingDO;
import per.kokabot.maimai.adapter.MaimaiAdapterClient;
import per.kokabot.mapper.MaimaiAccountBindingMapper;
import per.kokabot.security.TokenCipher;
import per.kokabot.service.MaimaiAccountBindingService;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.regex.Pattern;

@Service
public class MaimaiAccountBindingServiceImpl
        implements MaimaiAccountBindingService {

    private static final Pattern SGID_PATTERN =
            Pattern.compile("^SGWCMAID[A-Za-z0-9]+$");

    private final MaimaiAdapterClient adapterClient;
    private final MaimaiAccountBindingMapper bindingMapper;
    private final TokenCipher tokenCipher;
    private final Executor syncExecutor;

    public MaimaiAccountBindingServiceImpl(
            MaimaiAdapterClient adapterClient,
            MaimaiAccountBindingMapper bindingMapper,
            TokenCipher tokenCipher,
            @Qualifier("maimaiSyncExecutor") Executor syncExecutor
    ) {
        this.adapterClient = adapterClient;
        this.bindingMapper = bindingMapper;
        this.tokenCipher = tokenCipher;
        this.syncExecutor = syncExecutor;
    }

    @Override
    public CompletableFuture<Void> bind(long qqId, String sgid) {
        String normalized = sgid == null ? "" : sgid.trim();
        if (!SGID_PATTERN.matcher(normalized).matches()) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException(
                            "二维码内容格式无效，应以 SGWCMAID 开头"
                    )
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
            String credential = adapterClient.resolveArcadeCredential(normalized);
            MaimaiAccountBindingDO binding = new MaimaiAccountBindingDO();
            binding.setQqId(qqId);
            binding.setArcadeCredentialEncrypted(
                    tokenCipher.encrypt(credential)
            );
            bindingMapper.upsert(binding);
        }, syncExecutor);
    }

    @Override
    public boolean isBound(long qqId) {
        return bindingMapper.selectByQqId(qqId) != null;
    }

    @Override
    public String credential(long qqId) {
        MaimaiAccountBindingDO binding = bindingMapper.selectByQqId(qqId);
        if (binding == null) {
            throw new IllegalStateException(
                    "尚未绑定舞萌账号，请先私聊发送 /bind mai"
            );
        }
        if (!tokenCipher.isConfigured()) {
            throw new IllegalStateException(
                    "管理员尚未配置 TOKEN_ENCRYPTION_KEY"
            );
        }
        return tokenCipher.decrypt(binding.getArcadeCredentialEncrypted());
    }

    @Override
    public boolean unbind(long qqId) {
        return bindingMapper.deleteByQqId(qqId) > 0;
    }
}
