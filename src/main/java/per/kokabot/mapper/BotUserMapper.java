package per.kokabot.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import per.kokabot.entity.BotUserDO;
import per.kokabot.provider.MaimaiProviderType;

/**
 * @BelongsProject: maimai-bot
 * @BelongsPackage: per.kokabot.mapper
 * @Author: L
 * @CreateTime: 2026-08-29 18:13
 * @Description: TODO
 * @Version: 1.0
 */
@Mapper
public interface BotUserMapper {

    BotUserDO selectByQqId(@Param("qqId") long qqId);

    int upsertProvider(@Param("qqId") long qqId, @Param("provider") MaimaiProviderType provider);
}
