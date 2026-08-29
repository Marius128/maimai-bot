package per.kokabot.onebot;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import per.kokabot.command.context.CommandContext;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * @BelongsProject: maimai-bot
 * @BelongsPackage: per.kokabot.onebot
 * @Author: L
 * @CreateTime: 2026-08-29 16:30
 * @Description: TODO
 * @Version: 1.0
 */
@Component
public class OneBotMessageSender {

    private final OneBotClient oneBotClient;

    public OneBotMessageSender(OneBotClient oneBotClient) {
        this.oneBotClient = oneBotClient;
    }

    public CompletableFuture<JsonNode> reply(
            CommandContext context,
            String message
    ) {
        if ("private".equals(context.messageType())) {
            return oneBotClient.callAction(
                    "send_private_msg",
                    Map.of(
                            "user_id", context.userId(),
                            "message", message
                    )
            );
        }

        if ("group".equals(context.messageType())) {
            Long groupId = context.groupId();

            if (groupId == null) {
                return CompletableFuture.failedFuture(
                        new IllegalArgumentException("群消息缺少 groupId")
                );
            }

            return oneBotClient.callAction(
                    "send_group_msg",
                    Map.of(
                            "group_id", groupId,
                            "message", message
                    )
            );
        }

        return CompletableFuture.failedFuture(
                new IllegalArgumentException(
                        "不支持的消息类型：" + context.messageType()
                )
        );
    }
}
