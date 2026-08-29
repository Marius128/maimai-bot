package per.kokabot.maimai.model;

import java.util.List;

/**
 * @BelongsProject: maimai-bot
 * @BelongsPackage: per.kokabot.maimai.model
 * @Author: L
 * @CreateTime: 2026-08-29 17:20
 * @Description: TODO
 * @Version: 1.0
 */
public record B50Result(String playerName,
                        int rating,
                        List<ScoreRecord> best35,
                        List<ScoreRecord> best15) {
}
