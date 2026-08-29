package per.kokabot.maimai.model;

/**
 * @BelongsProject: maimai-bot
 * @BelongsPackage: per.kokabot.maimai.model
 * @Author: L
 * @CreateTime: 2026-08-29 17:20
 * @Description: TODO
 * @Version: 1.0
 */
public record  ScoreRecord(String songId,
                           String songName,
                           String chartType,
                           String difficulty,
                           String level,
                           double achievement,
                           int dxScore,
                           int rating) {
}
