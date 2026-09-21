package per.kokabot.command;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import per.kokabot.command.context.CommandContext;
import per.kokabot.onebot.OneBotMessageSender;
import per.kokabot.service.LxnsOAuthBindingService;
import per.kokabot.service.DivingFishBindingService;
import per.kokabot.service.DivingFishOAuthService;
import per.kokabot.service.MaimaiAccountBindingService;

import java.util.Locale;

@Component
public class UnbindCommandHandler implements CommandHandler {

    private static final Logger log =
            LoggerFactory.getLogger(UnbindCommandHandler.class);

    private final LxnsOAuthBindingService lxnsBindingService;
    private final DivingFishBindingService divingFishBindingService;
    private final DivingFishOAuthService divingFishOAuthService;
    private final MaimaiAccountBindingService maimaiBindingService;
    private final OneBotMessageSender messageSender;

    public UnbindCommandHandler(
            LxnsOAuthBindingService lxnsBindingService,
            DivingFishBindingService divingFishBindingService,
            DivingFishOAuthService divingFishOAuthService,
            MaimaiAccountBindingService maimaiBindingService,
            OneBotMessageSender messageSender
    ) {
        this.lxnsBindingService = lxnsBindingService;
        this.divingFishBindingService = divingFishBindingService;
        this.divingFishOAuthService = divingFishOAuthService;
        this.maimaiBindingService = maimaiBindingService;
        this.messageSender = messageSender;
    }

    @Override
    public String command() {
        return "unbind";
    }

    @Override
    public void handle(CommandContext context) {
        String target = context.arguments().trim().toLowerCase(Locale.ROOT);
        if (!"lxns".equals(target)
                && !"落雪".equals(target)
                && !"fish".equals(target)
                && !"水鱼".equals(target)
                && !"mai".equals(target)
                && !"舞萌".equals(target)) {
            reply(context, "用法：/unbind lxns、/unbind fish 或 /unbind mai");
            return;
        }
        if (!"private".equals(context.messageType())) {
            reply(context, "解绑涉及账号授权，请私聊机器人执行 /unbind lxns");
            return;
        }

        try {
            if ("mai".equals(target) || "舞萌".equals(target)) {
                boolean removed = maimaiBindingService.unbind(context.userId());
                reply(
                        context,
                        removed ? "已删除本机器人保存的舞萌玩家标识。"
                                : "你还没有绑定舞萌账号。"
                );
                return;
            }
            if ("fish".equals(target) || "水鱼".equals(target)) {
                boolean removed = divingFishBindingService.unbind(
                        context.userId()
                );
                divingFishOAuthService.clearCachedToken(context.userId());
                reply(
                        context,
                        (removed
                                ? "已删除本机器人保存的水鱼 Import-Token。"
                                : "本机器人没有保存你的水鱼 Import-Token。")
                                + "\n如需撤销 OAuth 授权，请在水鱼账号设置中撤销 kokabot。"
                );
                return;
            }

            boolean removed = lxnsBindingService.unbind(context.userId());
            reply(
                    context,
                    removed ? "已删除本机器人保存的落雪授权。你还可以在落雪的第三方应用页面撤销授权。"
                            : "你还没有绑定落雪。"
            );
        } catch (Exception error) {
            log.warn("解绑落雪失败: userId={}", context.userId(), error);
            reply(context, "解绑失败，请稍后重试。");
        }
    }

    private void reply(CommandContext context, String message) {
        messageSender.reply(context, message)
                .exceptionally(error -> {
                    log.warn("回复落雪解绑消息失败", error);
                    return null;
                });
    }
}
