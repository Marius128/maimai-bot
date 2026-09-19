package per.kokabot.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import per.kokabot.entity.MaimaiAccountBindingDO;

@Mapper
public interface MaimaiAccountBindingMapper {

    MaimaiAccountBindingDO selectByQqId(@Param("qqId") long qqId);

    int upsert(MaimaiAccountBindingDO binding);

    int deleteByQqId(@Param("qqId") long qqId);
}
