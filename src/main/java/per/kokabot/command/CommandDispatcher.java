package per.kokabot.command;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import per.kokabot.command.context.CommandContext;
import per.kokabot.onebot.OneBotEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @BelongsProject: maimai-bot
 * @BelongsPackage: per.kokabot.command
 * @Author: L
 * @CreateTime: 2026-08-29 14:58
 * @Description: TODO
 * @Version: 1.0
 */
@Component
public class CommandDispatcher {

    private static final Logger log =
            LoggerFactory.getLogger(CommandDispatcher.class);

    private final Map<String, CommandHandler> handlers = new HashMap<>();

    private String extractText(JsonNode payload) {
        JsonNode messageSegments = payload.path("message");

        if (!messageSegments.isArray()) {
            return payload.path("raw_message").asText();
        }

        StringBuilder text = new StringBuilder();

        for (JsonNode segment : messageSegments) {
            if ("text".equals(segment.path("type").asText())) {
                text.append(
                        segment.path("data").path("text").asText()
                );
            }
        }

        return text.toString();
    }

    private boolean isAtBot(JsonNode payload) {
        String selfId = payload.path("self_id").asText();
        JsonNode messageSegments = payload.path("message");

        if (!messageSegments.isArray()) {
            return false;
        }

        for (JsonNode segment : messageSegments) {
            String type = segment.path("type").asText();

            if (!"at".equals(type)) {
                continue;
            }

            String targetQq = segment.path("data").path("qq").asText();

            if (selfId.equals(targetQq)) {
                return true;
            }
        }

        return false;
    }

    public CommandDispatcher(List<CommandHandler> handlerList) {
        for (CommandHandler commandHandler : handlerList) {
            handlers.put(commandHandler.command(), commandHandler);
        }
    }

    @EventListener
    public void dispatch(OneBotEvent event) {
        JsonNode payload = event.payload();

        //判断是不是消息事件
        String postType = payload.path("post_type").asText();

        if (!"message".equals(postType)) {
            return;
        }

        //取得消息文字
        String messageType = payload.path("message_type").asText();

        //群消息必须 @ 当前机器人
        if ("group".equals(messageType) && !isAtBot(payload)) {
            return;
        }

        //忽略at消息段，只提取文字
        String rawMessage = extractText(payload).trim();

        if (!rawMessage.startsWith("/")) {
            return;
        }

        String commandText = rawMessage.substring(1).trim();

        if (commandText.isEmpty()) {
            return;
        }

        String[] parts = commandText.split("\\s+", 2);

        String commandName = parts[0];
        String arguments = parts.length == 2 ? parts[1] : "";

        log.debug(
                "收到命令: command={}, userId={}, messageType={}",
                commandName,
                payload.path("user_id").asLong(),
                messageType
        );

        CommandHandler handler = handlers.get(commandName);

        if (handler == null) {
            log.debug("未知命令: {}", commandName);
            return;
        }

        Long groupId = resolveGroupId(payload, messageType);

        CommandContext context = new CommandContext(
                payload.path("user_id").asLong(),
                groupId,
                messageType,
                payload.path("message_id").asLong(),
                payload.path("sender").path("nickname").asText(),
                commandName,
                arguments
        );

        handler.handle(context);
    }

    private Long resolveGroupId(JsonNode payload, String messageType) {
        if ("group".equals(messageType)) {
            long groupId = payload.path("group_id").asLong();
            return groupId > 0 ? groupId : null;
        }

        if (!"private".equals(messageType)) {
            return null;
        }

        // SnowLuma 将群临时会话的来源群号放在 sender.group_id。
        // 同时兼容部分 OneBot 实现使用顶层 group_id 的情况。
        long groupId = payload.path("sender").path("group_id").asLong();
        if (groupId <= 0) {
            groupId = payload.path("group_id").asLong();
        }

        return groupId > 0 ? groupId : null;
    }
}
