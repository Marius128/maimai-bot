package per.kokabot.provider.divingfish.request;

/**
 * @BelongsProject: maimai-bot
 * @BelongsPackage: per.kokabot.provider.divingfish.request
 * @Author: L
 * @CreateTime: 2026-08-30 22:11
 * @Description: TODO
 * @Version: 1.0
 */
public record DivingFishB50Request(String qq, boolean b50) {

    public static DivingFishB50Request of(long qqId) {
        return new DivingFishB50Request(
                String.valueOf(qqId), true
        );
    }
}
