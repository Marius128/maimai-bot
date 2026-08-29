package per.kokabot.provider;

import per.kokabot.maimai.model.B50Result;

import java.util.concurrent.CompletableFuture;

/**
 * @BelongsProject: maimai-bot
 * @BelongsPackage: per.kokabot.provider
 * @Author: L
 * @CreateTime: 2026-08-29 17:35
 * @Description: TODO
 * @Version: 1.0
 */
public interface MaimaiScoreProvider {
    /**
     * 当前实现对应的数据源。
     */
    MaimaiProviderType type();

    /**
     * 根据 QQ 查询 B50，并转换成统一的 B50Result。
     */
    CompletableFuture<B50Result> queryB50(long qqId);
}
