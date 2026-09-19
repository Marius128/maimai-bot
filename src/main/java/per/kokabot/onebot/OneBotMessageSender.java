package per.kokabot.onebot;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import per.kokabot.command.context.CommandContext;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
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
        return send(context, message);
    }

    public CompletableFuture<JsonNode> replyImage(
            CommandContext context,
            byte[] pngData
    ) {
        if (pngData == null || pngData.length == 0) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("图片数据不能为空")
            );
        }

        String base64 = Base64.getEncoder().encodeToString(pngData);
        List<Map<String, Object>> message = List.of(
                Map.of(
                        "type", "image",
                        "data", Map.of(
                                "file", "base64://" + base64
                        )
                )
        );

        return send(context, message);
    }

    private CompletableFuture<JsonNode> send(
            CommandContext context,
            Object message
    ) {
        if ("private".equals(context.messageType())) {
            Map<String, Object> params = new HashMap<>();
            params.put("user_id", context.userId());
            params.put("message", message);

            // 群临时会话必须携带来源群号，否则 QQ 会按陌生人私聊拒绝发送。
            if (context.groupId() != null && context.groupId() > 0) {
                params.put("group_id", context.groupId());
            }

            return oneBotClient.callAction(
                    "send_private_msg",
                    params
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
