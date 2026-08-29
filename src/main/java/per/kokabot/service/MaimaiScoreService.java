package per.kokabot.service;

import per.kokabot.maimai.model.B50Result;

import java.util.concurrent.CompletableFuture;

/**
 * @BelongsProject: maimai-bot
 * @BelongsPackage: per.kokabot.service
 * @Author: L
 * @CreateTime: 2026-08-29 17:22
 * @Description: TODO
 * @Version: 1.0
 */
public interface MaimaiScoreService {

    /**
     * 根据 QQ 号查询玩家 B50。
     */
    CompletableFuture<B50Result> queryB50(long qqId);
}
