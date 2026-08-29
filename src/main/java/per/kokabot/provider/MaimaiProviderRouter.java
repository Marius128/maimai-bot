package per.kokabot.provider;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * @BelongsProject: maimai-bot
 * @BelongsPackage: per.kokabot.provider
 * @Author: L
 * @CreateTime: 2026-08-29 17:43
 * @Description: TODO
 * @Version: 1.0
 */
@Component
public class MaimaiProviderRouter {

    private final Map<MaimaiProviderType, MaimaiScoreProvider> providers =
            new EnumMap<>(MaimaiProviderType.class);

    public MaimaiProviderRouter(
            List<MaimaiScoreProvider> providerList
    ) {
        for (MaimaiScoreProvider provider : providerList) {
            MaimaiScoreProvider oldProvider =
                    providers.put(provider.type(), provider);

            if (oldProvider != null) {
                throw new IllegalStateException(
                        "重复的数据源实现：" + provider.type()
                );
            }
        }
    }

    public MaimaiScoreProvider getProvider(
            MaimaiProviderType type
    ) {
        MaimaiScoreProvider provider = providers.get(type);

        if (provider == null) {
            throw new IllegalArgumentException(
                    "数据源尚未实现：" + type.displayName()
            );
        }

        return provider;
    }
}
