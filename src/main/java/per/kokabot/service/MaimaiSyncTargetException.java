package per.kokabot.service;

import per.kokabot.provider.MaimaiProviderType;

public class MaimaiSyncTargetException extends RuntimeException {

    private final MaimaiProviderType provider;

    public MaimaiSyncTargetException(
            MaimaiProviderType provider,
            Throwable cause
    ) {
        super("同步到" + provider.displayName() + "失败", cause);
        this.provider = provider;
    }

    public MaimaiProviderType provider() {
        return provider;
    }
}
