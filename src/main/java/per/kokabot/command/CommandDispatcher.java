package per.kokabot.command;

import com.fasterxml.jackson.databind.JsonNode;
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

        System.out.println("收到的消息：" + rawMessage);

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

        System.out.println("命令名称：" + commandName);
        System.out.println("命令参数：" + arguments);

        CommandHandler handler = handlers.get(commandName);

        if (handler == null) {
            System.out.println("位置命令 " + commandName);
            return;
        }

        Long groupId = "group".equals(messageType)
                ? payload.path("group_id").asLong()
                : null;

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
}
