package per.kokabot.provider;

/**
 * @BelongsProject: maimai-bot
 * @BelongsPackage: per.kokabot.provider
 * @Author: L
 * @CreateTime: 2026-08-29 17:37
 * @Description: TODO
 * @Version: 1.0
 */
public enum MaimaiProviderType {

    DIVING_FISH("水鱼"),
    LXNS("落雪");

    private final String displayName;

    MaimaiProviderType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
