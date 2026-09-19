package per.kokabot.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import per.kokabot.entity.LxnsOAuthBindingDO;

@Mapper
public interface LxnsOAuthBindingMapper {

    LxnsOAuthBindingDO selectByQqId(@Param("qqId") long qqId);

    int upsert(LxnsOAuthBindingDO binding);

    int deleteByQqId(@Param("qqId") long qqId);
}
