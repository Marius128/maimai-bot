package per.kokabot.service;

import per.kokabot.provider.MaimaiProviderType;

/**
 * @BelongsProject: maimai-bot
 * @BelongsPackage: per.kokabot.service
 * @Author: L
 * @CreateTime: 2026-08-29 18:36
 * @Description: TODO
 * @Version: 1.0
 */
public interface MaimaiProviderPreferenceService {

    MaimaiProviderType getProvider(long userId);

    void setProvider(long userId, MaimaiProviderType providerType);
}
