package per.kokabot.command;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import per.kokabot.onebot.OneBotClient;

import java.util.Map;

/**
 * @BelongsProject: maimai-bot
 * @BelongsPackage: per.kokabot.command
 * @Author: L
 * @CreateTime: 2026-08-29 15:23
 * @Description: TODO
 * @Version: 1.0
 */
@Component
public class B50CommandHandler implements CommandHandler{

    private final OneBotClient oneBotClient;

    public B50CommandHandler(OneBotClient oneBotClient) {
        this.oneBotClient = oneBotClient;
    }

    @Override
    public String command() {
        return "b50";
    }

    @Override
    public void handle(JsonNode payload, String arguments) {
        String messageType = payload.path("message_type").asText();
        long userId = payload.path("user_id").asLong();

        if ("private".equals(messageType)) {
            oneBotClient.callAction(
                    "send_private_msg",
                    Map.of(
                            "user_id", userId,
                            "message", "B50 查询功能正在开发中"
                    )
            ).exceptionally(error -> {
                System.out.println("消息发送失败：" + error.getMessage());
                return null;
            });
        }
    }
}
