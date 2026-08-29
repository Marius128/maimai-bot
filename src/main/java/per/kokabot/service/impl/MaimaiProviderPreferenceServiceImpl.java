package per.kokabot.service.impl;

import org.springframework.stereotype.Service;
import per.kokabot.entity.BotUserDO;
import per.kokabot.mapper.BotUserMapper;
import per.kokabot.provider.MaimaiProviderType;
import per.kokabot.service.MaimaiProviderPreferenceService;

import java.util.Objects;

/**
 * @BelongsProject: maimai-bot
 * @BelongsPackage: per.kokabot.service.impl
 * @Author: L
 * @CreateTime: 2026-08-29 18:35
 * @Description: TODO
 * @Version: 1.0
 */
@Service
public class MaimaiProviderPreferenceServiceImpl implements MaimaiProviderPreferenceService {

    private static final MaimaiProviderType DEFAULT_PROVIDER = MaimaiProviderType.DIVING_FISH;

    private final BotUserMapper botUserMapper;

    public MaimaiProviderPreferenceServiceImpl(BotUserMapper botUserMapper) {
        this.botUserMapper = botUserMapper;
    }

    @Override
    public MaimaiProviderType getProvider(long userId) {
        BotUserDO botUser = botUserMapper.selectByQqId(userId);

        if (botUser == null || botUser.getProvider() == null) {
            return DEFAULT_PROVIDER;
        }

        return botUser.getProvider();
    }

    @Override
    public void setProvider(long userId, MaimaiProviderType providerType) {
            Objects.requireNonNull(
                    providerType,
                    "providerType 不能为空"
            );

            botUserMapper.upsertProvider(userId, providerType);
    }
}
