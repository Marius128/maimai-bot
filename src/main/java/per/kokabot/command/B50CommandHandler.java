package per.kokabot.command;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import per.kokabot.command.context.CommandContext;
import per.kokabot.onebot.OneBotMessageSender;
import per.kokabot.provider.divingfish.DivingFishApiException;
import per.kokabot.provider.lxns.LxnsApiException;
import per.kokabot.provider.lxns.LxnsConfigurationException;
import per.kokabot.service.B50ImageService;
import per.kokabot.service.MaimaiScoreService;

import java.util.concurrent.CompletionException;

/**
 * @BelongsProject: maimai-bot
 * @BelongsPackage: per.kokabot.command
 * @Author: L
 * @CreateTime: 2026-08-29 15:23
 * @Description: TODO
 * @Version: 1.0
 */
@Component
public class B50CommandHandler implements CommandHandler {

    private static final Logger log =
            LoggerFactory.getLogger(B50CommandHandler.class);

    private final OneBotMessageSender messageSender;
    private final MaimaiScoreService scoreService;
    private final B50ImageService imageService;

    public B50CommandHandler(
            OneBotMessageSender messageSender,
            MaimaiScoreService scoreService,
            B50ImageService imageService
    ) {
        this.messageSender = messageSender;
        this.scoreService = scoreService;
        this.imageService = imageService;
    }

    @Override
    public String command() {
        return "b50";
    }

    @Override
    public void handle(CommandContext context) {
        scoreService.queryB50(context.userId())
                .thenCompose(imageService::render)
                .handle((imageData, error) -> {
                    if (error != null) {
                        Throwable cause = unwrap(error);
                        log.warn(
                                "查询或生成 B50 失败: userId={}, error={}",
                                context.userId(),
                                cause.toString()
                        );
                        return ReplyContent.text(
                                friendlyErrorMessage(cause)
                        );
                    }
                    return ReplyContent.image(imageData);
                })
                .thenCompose(content -> content.imageData() == null
                        ? messageSender.reply(context, content.text())
                        : messageSender.replyImage(
                                context,
                                content.imageData()
                        ))
                .exceptionally(error -> {
                    log.warn("回复 B50 消息失败", error);
                    return null;
                });
    }

    private String friendlyErrorMessage(Throwable error) {
        if (error instanceof DivingFishApiException apiError) {
            return switch (apiError.statusCode()) {
                case 400 -> "水鱼中未找到你的成绩。\n"
                        + "请先注册或登录水鱼查分器，绑定当前发指令的 QQ，"
                        + "并上传一次舞萌成绩：\n"
                        + "https://maimai.diving-fish.com/";
                case 403 -> "该玩家禁止其他人获取成绩数据。";
                default -> "水鱼查分服务暂时不可用，请稍后重试。";
            };
        }

        if (error instanceof LxnsConfigurationException) {
            return "管理员尚未配置落雪 API 密钥。";
        }

        if (error instanceof LxnsApiException apiError) {
            return switch (apiError.statusCode()) {
                case 400, 404 -> "落雪中未找到你的成绩。\n"
                        + "请先注册或登录落雪查分器，绑定当前发指令的 QQ，"
                        + "并同步一次舞萌成绩：\n"
                        + "https://maimai.lxns.net/\n"
                        + "同步教程：https://maimai.lxns.net/docs/sync";
                case 401 -> "落雪 API 密钥无效，请联系管理员。";
                case 403 -> "该玩家未允许第三方获取成绩。";
                case 429 -> "落雪请求过于频繁，请稍后重试。";
                default -> "落雪查分服务暂时不可用，请稍后重试。";
            };
        }

        return "B50 查询失败，请稍后重试。";
    }

    private Throwable unwrap(Throwable error) {
        Throwable current = error;
        while (current instanceof CompletionException
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private record ReplyContent(String text, byte[] imageData) {

        private static ReplyContent text(String text) {
            return new ReplyContent(text, null);
        }

        private static ReplyContent image(byte[] imageData) {
            return new ReplyContent(null, imageData);
        }
    }
}
