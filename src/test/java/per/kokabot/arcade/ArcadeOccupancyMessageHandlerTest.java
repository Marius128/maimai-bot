package per.kokabot.arcade;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ArcadeOccupancyMessageHandlerTest {

    @Test
    void parsesAllReportAliases() {
        assertReport("g5", ArcadeVenue.GUANG_DA, 5);
        assertReport("光大 5", ArcadeVenue.GUANG_DA, 5);
        assertReport("光5", ArcadeVenue.GUANG_DA, 5);
        assertReport("Y12", ArcadeVenue.YONG_LI, 12);
        assertReport("永利12", ArcadeVenue.YONG_LI, 12);
        assertReport("永 12", ArcadeVenue.YONG_LI, 12);
    }

    @Test
    void parsesAllQueryAliases() {
        assertQuery("g几", ArcadeVenue.GUANG_DA);
        assertQuery("gj", ArcadeVenue.GUANG_DA);
        assertQuery("光大几个", ArcadeVenue.GUANG_DA);
        assertQuery("光大几", ArcadeVenue.GUANG_DA);
        assertQuery("光几", ArcadeVenue.GUANG_DA);
        assertQuery("y几", ArcadeVenue.YONG_LI);
        assertQuery("yj", ArcadeVenue.YONG_LI);
        assertQuery("永利几个", ArcadeVenue.YONG_LI);
        assertQuery("永利几", ArcadeVenue.YONG_LI);
        assertQuery("永几", ArcadeVenue.YONG_LI);
    }

    @Test
    void ignoresMessagesThatOnlyContainAnAlias() {
        assertThat(ArcadeOccupancyMessageHandler.parse("今天光大5个人"))
                .isNull();
        assertThat(ArcadeOccupancyMessageHandler.parse("g100"))
                .isNull();
        assertThat(ArcadeOccupancyMessageHandler.parse("光大"))
                .isNull();
    }

    @Test
    void estimatesWaitingTimeForOneMachine() {
        assertThat(ArcadeOccupancyMessageHandler.waitingMessage(0))
                .contains("无需等待");
        assertThat(ArcadeOccupancyMessageHandler.waitingMessage(1))
                .contains("单人一轮约 10 分钟");
        assertThat(ArcadeOccupancyMessageHandler.waitingMessage(2))
                .contains("1 轮", "15 分钟");
        assertThat(ArcadeOccupancyMessageHandler.waitingMessage(5))
                .contains("2 轮", "30 分钟");
    }

    @Test
    void buildsVenueSpecificMessageWhenTodayHasNoReport() {
        assertThat(ArcadeOccupancyMessageHandler.notUpdatedMessage(
                ArcadeVenue.YONG_LI
        )).contains("今日永利人数尚未更新", "爽霸机")
                .doesNotContain("y5", "汇报现场人数");
        assertThat(ArcadeOccupancyMessageHandler.notUpdatedMessage(
                ArcadeVenue.GUANG_DA
        )).contains("今日光大人数尚未更新", "爽霸机")
                .doesNotContain("g5", "汇报现场人数");
    }

    @Test
    void prefersGroupCardForReporterDisplayName() throws Exception {
        JsonNode payload = new ObjectMapper().readTree("""
                {
                  "sender": {
                    "card": "群名片",
                    "nickname": "QQ昵称"
                  }
                }
                """);

        assertThat(ArcadeOccupancyMessageHandler.reporterDisplayName(
                payload,
                123456L
        )).isEqualTo("群名片");
    }

    @Test
    void fallsBackToNicknameWhenGroupCardIsEmpty() throws Exception {
        JsonNode payload = new ObjectMapper().readTree("""
                {
                  "sender": {
                    "card": "",
                    "nickname": "QQ昵称"
                  }
                }
                """);

        assertThat(ArcadeOccupancyMessageHandler.reporterDisplayName(
                payload,
                123456L
        )).isEqualTo("QQ昵称");
    }

    private void assertReport(
            String input,
            ArcadeVenue venue,
            int playerCount
    ) {
        ArcadeOccupancyMessageHandler.ParsedMessage parsed =
                ArcadeOccupancyMessageHandler.parse(input);
        assertThat(parsed).isNotNull();
        assertThat(parsed.venue()).isEqualTo(venue);
        assertThat(parsed.playerCount()).isEqualTo(playerCount);
    }

    private void assertQuery(String input, ArcadeVenue venue) {
        ArcadeOccupancyMessageHandler.ParsedMessage parsed =
                ArcadeOccupancyMessageHandler.parse(input);
        assertThat(parsed).isNotNull();
        assertThat(parsed.venue()).isEqualTo(venue);
        assertThat(parsed.playerCount()).isNull();
    }
}
