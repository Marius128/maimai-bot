package per.kokabot.service;

import per.kokabot.arcade.ArcadeVenue;

import java.time.Instant;

public interface ArcadeOccupancyService {

    void report(
            long groupId,
            ArcadeVenue venue,
            int playerCount,
            long reporterUserId,
            String reporterDisplayName,
            Instant reportedAt
    );

    OccupancyReport find(long groupId, ArcadeVenue venue);

    record OccupancyReport(
            ArcadeVenue venue,
            int playerCount,
            long reporterUserId,
            String reporterDisplayName,
            Instant reportedAt
    ) {
    }
}
