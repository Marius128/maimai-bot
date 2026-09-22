package per.kokabot.service.impl;

import org.junit.jupiter.api.Test;
import per.kokabot.arcade.ArcadeVenue;
import per.kokabot.entity.ArcadeOccupancyReportDO;
import per.kokabot.mapper.ArcadeOccupancyReportMapper;
import per.kokabot.service.ArcadeOccupancyService;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ArcadeOccupancyServiceImplTest {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    @Test
    void ignoresReportFromPreviousShanghaiDay() {
        ArcadeOccupancyReportMapper mapper = mock(
                ArcadeOccupancyReportMapper.class
        );
        ArcadeOccupancyServiceImpl service =
                new ArcadeOccupancyServiceImpl(mapper);
        when(mapper.select(100L, ArcadeVenue.YONG_LI))
                .thenReturn(reportAt(startOfToday().minusSeconds(1)));

        assertThat(service.find(100L, ArcadeVenue.YONG_LI)).isNull();
    }

    @Test
    void returnsReportFromCurrentShanghaiDay() {
        ArcadeOccupancyReportMapper mapper = mock(
                ArcadeOccupancyReportMapper.class
        );
        ArcadeOccupancyServiceImpl service =
                new ArcadeOccupancyServiceImpl(mapper);
        Instant reportedAt = startOfToday().plusSeconds(1);
        when(mapper.select(100L, ArcadeVenue.GUANG_DA))
                .thenReturn(reportAt(reportedAt));

        ArcadeOccupancyService.OccupancyReport result =
                service.find(100L, ArcadeVenue.GUANG_DA);

        assertThat(result).isNotNull();
        assertThat(result.playerCount()).isEqualTo(3);
        assertThat(result.reporterDisplayName()).isEqualTo("测试玩家");
        assertThat(result.reportedAt()).isEqualTo(reportedAt);
    }

    private Instant startOfToday() {
        return LocalDate.now(SHANGHAI).atStartOfDay(SHANGHAI).toInstant();
    }

    private ArcadeOccupancyReportDO reportAt(Instant instant) {
        ArcadeOccupancyReportDO report = new ArcadeOccupancyReportDO();
        report.setVenue(ArcadeVenue.GUANG_DA);
        report.setPlayerCount(3);
        report.setReporterUserId(200L);
        report.setReporterDisplayName("测试玩家");
        report.setReportedAt(Timestamp.from(instant));
        return report;
    }
}
