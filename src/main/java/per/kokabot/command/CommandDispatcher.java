package per.kokabot.command;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
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

        // 取得消息文字
        String rawMessage = payload.path("raw_message").asText().trim();
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

        handler.handle(payload, arguments);
    }
}
