package per.kokabot.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import per.kokabot.entity.DivingFishBindingDO;

@Mapper
public interface DivingFishBindingMapper {

    DivingFishBindingDO selectByQqId(@Param("qqId") long qqId);

    int upsert(DivingFishBindingDO binding);

    int deleteByQqId(@Param("qqId") long qqId);
}
