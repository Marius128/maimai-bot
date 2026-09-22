package per.kokabot.service.impl;

import org.springframework.stereotype.Service;
import per.kokabot.arcade.ArcadeVenue;
import per.kokabot.entity.ArcadeOccupancyReportDO;
import per.kokabot.mapper.ArcadeOccupancyReportMapper;
import per.kokabot.service.ArcadeOccupancyService;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

@Service
public class ArcadeOccupancyServiceImpl implements ArcadeOccupancyService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final ArcadeOccupancyReportMapper reportMapper;

    public ArcadeOccupancyServiceImpl(
            ArcadeOccupancyReportMapper reportMapper
    ) {
        this.reportMapper = reportMapper;
    }

    @Override
    public void report(
            long groupId,
            ArcadeVenue venue,
            int playerCount,
            long reporterUserId,
            String reporterDisplayName,
            Instant reportedAt
    ) {
        if (groupId <= 0 || reporterUserId <= 0) {
            throw new IllegalArgumentException("群号和汇报用户不能为空");
        }
        if (playerCount < 0 || playerCount > 99) {
            throw new IllegalArgumentException("机厅人数必须在 0 到 99 之间");
        }

        ArcadeOccupancyReportDO report = new ArcadeOccupancyReportDO();
        report.setGroupId(groupId);
        report.setVenue(venue);
        report.setPlayerCount(playerCount);
        report.setReporterUserId(reporterUserId);
        report.setReporterDisplayName(reporterDisplayName);
        report.setReportedAt(Timestamp.from(
                reportedAt == null ? Instant.now() : reportedAt
        ));
        reportMapper.upsert(report);
    }

    @Override
    public OccupancyReport find(long groupId, ArcadeVenue venue) {
        ArcadeOccupancyReportDO report = reportMapper.select(groupId, venue);
        if (report == null || !isToday(report.getReportedAt().toInstant())) {
            return null;
        }
        return new OccupancyReport(
                report.getVenue(),
                report.getPlayerCount(),
                report.getReporterUserId(),
                displayName(report),
                report.getReportedAt().toInstant()
        );
    }

    private String displayName(ArcadeOccupancyReportDO report) {
        String displayName = report.getReporterDisplayName();
        if (displayName == null || displayName.isBlank()) {
            return String.valueOf(report.getReporterUserId());
        }
        return displayName;
    }

    static boolean isToday(Instant reportedAt) {
        LocalDate reportDate = reportedAt.atZone(BUSINESS_ZONE).toLocalDate();
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        return reportDate.equals(today);
    }
}
