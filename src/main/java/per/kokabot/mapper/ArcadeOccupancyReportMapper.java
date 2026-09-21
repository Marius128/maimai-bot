package per.kokabot.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import per.kokabot.arcade.ArcadeVenue;
import per.kokabot.entity.ArcadeOccupancyReportDO;

@Mapper
public interface ArcadeOccupancyReportMapper {

    ArcadeOccupancyReportDO select(
            @Param("groupId") long groupId,
            @Param("venue") ArcadeVenue venue
    );

    int upsert(ArcadeOccupancyReportDO report);
}
