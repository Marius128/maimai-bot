package per.kokabot.entity;

import lombok.Data;
import per.kokabot.arcade.ArcadeVenue;

import java.sql.Timestamp;

@Data
public class ArcadeOccupancyReportDO {

    private Long groupId;
    private ArcadeVenue venue;
    private Integer playerCount;
    private Long reporterUserId;
    private String reporterDisplayName;
    private Timestamp reportedAt;
    private Timestamp createdAt;
    private Timestamp updatedAt;
}
