package per.kokabot.arcade;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import per.kokabot.onebot.OneBotEvent;
import per.kokabot.onebot.OneBotMessageSender;
import per.kokabot.service.ArcadeOccupancyService;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ArcadeOccupancyMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(
            ArcadeOccupancyMessageHandler.class
    );
    private static final Pattern REPORT_PATTERN = Pattern.compile(
            "^(g|光大|光|y|永利|永)(\\d{1,2})$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Set<String> GUANG_DA_QUERIES = Set.of(
            "g几", "gj", "光大几个", "光大几", "光几"
    );
    private static final Set<String> YONG_LI_QUERIES = Set.of(
            "y几", "yj", "永利几个", "永利几", "永几"
    );
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(ZoneId.of("Asia/Shanghai"));

    private final ArcadeOccupancyService occupancyService;
    private final OneBotMessageSender messageSender;

    public ArcadeOccupancyMessageHandler(
            ArcadeOccupancyService occupancyService,
            OneBotMessageSender messageSender
    ) {
        this.occupancyService = occupancyService;
        this.messageSender = messageSender;
    }

    @EventListener
    public void handle(OneBotEvent event) {
        JsonNode payload = event.payload();
        if (!"message".equals(payload.path("post_type").asText())
                || !"group".equals(
                payload.path("message_type").asText()
        )) {
            return;
        }
        long groupId = payload.path("group_id").asLong();
        long userId = payload.path("user_id").asLong();
        long selfId = payload.path("self_id").asLong();
        if (groupId <= 0 || userId <= 0 || userId == selfId) {
            return;
        }

        ParsedMessage parsed = parse(extractText(payload));
        if (parsed == null) {
            return;
        }

        try {
            if (parsed.playerCount() != null) {
                handleReport(payload, groupId, userId, parsed);
            } else {
                handleQuery(groupId, parsed.venue());
            }
        } catch (Exception error) {
            log.warn(
                    "处理机厅人数消息失败: groupId={}, userId={}, error={}",
                    groupId,
                    userId,
                    error.toString()
            );
            messageSender.sendGroupText(
                    groupId,
                    "机厅人数处理失败，请稍后重试。"
            ).exceptionally(sendError -> null);
        }
    }

    private void handleReport(
            JsonNode payload,
            long groupId,
            long userId,
            ParsedMessage parsed
    ) {
        long eventSeconds = payload.path("time").asLong();
        Instant reportedAt = eventSeconds > 0
                ? Instant.ofEpochSecond(eventSeconds)
                : Instant.now();
        occupancyService.report(
                groupId,
                parsed.venue(),
                parsed.playerCount(),
                userId,
                reportedAt
        );
        String message = "📍 " + parsed.venue().displayName()
                + "人数已更新为 " + parsed.playerCount() + " 人\n"
                + "🕹️ 机台数量：1 台（每轮 2 人）\n"
                + waitingMessage(parsed.playerCount());
        messageSender.sendGroupText(groupId, message)
                .exceptionally(error -> {
                    log.warn("回复机厅人数汇报失败", error);
                    return null;
                });
    }

    private void handleQuery(long groupId, ArcadeVenue venue) {
        ArcadeOccupancyService.OccupancyReport report =
                occupancyService.find(groupId, venue);
        if (report == null) {
            messageSender.sendGroupText(
                    groupId,
                    "暂无" + venue.displayName() + "人数汇报。\n"
                            + "可以发送 " + venue.shortCode()
                            + "5 这样的消息进行汇报。"
            ).exceptionally(error -> null);
            return;
        }

        String prefix = "📍 " + venue.displayName() + "当前人数："
                + report.playerCount() + " 人\n"
                + "🕹️ 机台数量：1 台（每轮 2 人）\n"
                + waitingMessage(report.playerCount())
                + "\n📣 汇报人：";
        String suffix = "\n🕒 汇报时间："
                + TIME_FORMATTER.format(report.reportedAt());
        messageSender.sendGroupWithMention(
                groupId,
                prefix,
                report.reporterUserId(),
                suffix
        ).exceptionally(error -> {
            log.warn("回复机厅人数查询失败", error);
            return null;
        });
    }

    static ParsedMessage parse(String rawText) {
        String text = rawText == null
                ? ""
                : rawText.replaceAll("\\s+", "")
                .toLowerCase(Locale.ROOT);
        Matcher report = REPORT_PATTERN.matcher(text);
        if (report.matches()) {
            ArcadeVenue venue = venueOf(report.group(1));
            return new ParsedMessage(
                    venue,
                    Integer.parseInt(report.group(2))
            );
        }
        if (GUANG_DA_QUERIES.contains(text)) {
            return new ParsedMessage(ArcadeVenue.GUANG_DA, null);
        }
        if (YONG_LI_QUERIES.contains(text)) {
            return new ParsedMessage(ArcadeVenue.YONG_LI, null);
        }
        return null;
    }

    static String waitingMessage(int playerCount) {
        if (playerCount == 0) {
            return "✅ 当前无人，无需等待。";
        }
        if (playerCount == 1) {
            return "✅ 当前仅 1 人，可直接拼机；单人一轮约 10 分钟。";
        }
        int rounds = playerCount / 2;
        int minutes = rounds * 15;
        return "⏳ 新到预计等待 " + rounds + " 轮（约 "
                + minutes + " 分钟）。";
    }

    private static ArcadeVenue venueOf(String alias) {
        return switch (alias.toLowerCase(Locale.ROOT)) {
            case "g", "光大", "光" -> ArcadeVenue.GUANG_DA;
            case "y", "永利", "永" -> ArcadeVenue.YONG_LI;
            default -> throw new IllegalArgumentException(
                    "未知机厅：" + alias
            );
        };
    }

    private static String extractText(JsonNode payload) {
        JsonNode segments = payload.path("message");
        if (!segments.isArray()) {
            return payload.path("raw_message").asText("");
        }
        StringBuilder text = new StringBuilder();
        for (JsonNode segment : segments) {
            if ("text".equals(segment.path("type").asText())) {
                text.append(segment.path("data").path("text").asText(""));
            }
        }
        return text.toString();
    }

    record ParsedMessage(ArcadeVenue venue, Integer playerCount) {
    }
}
