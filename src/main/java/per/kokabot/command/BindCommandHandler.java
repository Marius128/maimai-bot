package per.kokabot.command;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import per.kokabot.command.context.CommandContext;
import per.kokabot.onebot.OneBotMessageSender;
import per.kokabot.maimai.adapter.MaimaiAdapterException;
import per.kokabot.provider.lxns.oauth.LxnsOAuthException;
import per.kokabot.service.LxnsOAuthBindingService;
import per.kokabot.service.DivingFishBindingService;
import per.kokabot.service.MaimaiAccountBindingService;

import java.util.Locale;
import java.util.concurrent.CompletionException;

@Component
public class BindCommandHandler implements CommandHandler {

    private static final Logger log =
            LoggerFactory.getLogger(BindCommandHandler.class);

    private final LxnsOAuthBindingService lxnsBindingService;
    private final DivingFishBindingService divingFishBindingService;
    private final MaimaiAccountBindingService maimaiBindingService;
    private final OneBotMessageSender messageSender;

    public BindCommandHandler(
            LxnsOAuthBindingService lxnsBindingService,
            DivingFishBindingService divingFishBindingService,
            MaimaiAccountBindingService maimaiBindingService,
            OneBotMessageSender messageSender
    ) {
        this.lxnsBindingService = lxnsBindingService;
        this.divingFishBindingService = divingFishBindingService;
        this.maimaiBindingService = maimaiBindingService;
        this.messageSender = messageSender;
    }

    @Override
    public String command() {
        return "bind";
    }

    @Override
    public void handle(CommandContext context) {
        String input = context.arguments().trim();
        if (input.isBlank()) {
            reply(context, "用法：/bind lxns、/bind fish 或 /bind mai");
            return;
        }

        String[] parts = input.split("\\s+", 2);
        String target = parts[0].toLowerCase(Locale.ROOT);
        if (!"lxns".equals(target)
                && !"落雪".equals(target)
                && !"fish".equals(target)
                && !"水鱼".equals(target)
                && !"mai".equals(target)
                && !"舞萌".equals(target)) {
            reply(context, "当前支持：/bind lxns、/bind fish 或 /bind mai");
            return;
        }

        if (!"private".equals(context.messageType())) {
            reply(context, "绑定涉及敏感授权信息，请私聊机器人执行绑定命令。");
            return;
        }

        if ("mai".equals(target) || "舞萌".equals(target)) {
            handleMaimaiBinding(context, parts);
            return;
        }
        if ("fish".equals(target) || "水鱼".equals(target)) {
            handleDivingFishBinding(context, parts);
            return;
        }

        if (parts.length == 2
                && "status".equals(parts[1].trim().toLowerCase(Locale.ROOT))) {
            showStatus(context);
            return;
        }

        if (parts.length == 1 || parts[1].isBlank()) {
            beginAuthorization(context);
            return;
        }

        completeAuthorization(context, parts[1]);
    }

    private void handleDivingFishBinding(
            CommandContext context,
            String[] parts
    ) {
        if (parts.length == 2
                && "status".equals(parts[1].trim().toLowerCase(Locale.ROOT))) {
            reply(
                    context,
                    divingFishBindingService.isBound(context.userId())
                            ? "水鱼 Import-Token 已绑定，可以使用快捷更新。"
                            : "尚未绑定水鱼，请执行 /bind fish。"
            );
            return;
        }
        if (parts.length == 1 || parts[1].isBlank()) {
            reply(
                    context,
                    "请登录水鱼查分器，在个人资料页生成并复制 Import-Token，"
                            + "然后私聊发送：\n/bind fish Import-Token\n"
                            + "Import-Token 属于敏感凭证，请勿在群聊发送。"
            );
            return;
        }

        messageSender.reply(context, "正在验证水鱼 Import-Token，请稍候……")
                .exceptionally(error -> null);
        divingFishBindingService.bind(context.userId(), parts[1])
                .thenCompose(ignored -> messageSender.reply(
                        context,
                        "水鱼绑定成功，Import-Token 已加密保存。"
                ))
                .exceptionally(error -> {
                    Throwable cause = unwrap(error);
                    log.warn(
                            "绑定水鱼失败: userId={}, error={}",
                            context.userId(),
                            cause.toString()
                    );
                    reply(context, friendlyDivingFishMessage(cause));
                    return null;
                });
    }

    private String friendlyDivingFishMessage(Throwable error) {
        if (error instanceof MaimaiAdapterException adapterError) {
            if (adapterError.statusCode() == 0) {
                return "舞萌数据适配器尚未启动，请联系管理员。";
            }
            if (adapterError.statusCode() == 400
                    || adapterError.statusCode() == 401
                    || adapterError.statusCode() == 403) {
                return "水鱼 Import-Token 无效，请在水鱼个人资料页重新生成。";
            }
            return "暂时无法验证水鱼 Import-Token，请稍后重试。";
        }
        if (error instanceof IllegalStateException
                || error instanceof IllegalArgumentException) {
            return error.getMessage();
        }
        return "水鱼绑定失败，请稍后重试。";
    }

    private void handleMaimaiBinding(
            CommandContext context,
            String[] parts
    ) {
        if (parts.length == 2
                && "status".equals(parts[1].trim().toLowerCase(Locale.ROOT))) {
            reply(
                    context,
                    maimaiBindingService.isBound(context.userId())
                            ? "舞萌账号已绑定，可以使用快捷更新。"
                            : "尚未绑定舞萌账号。"
            );
            return;
        }
        if (parts.length == 1 || parts[1].isBlank()) {
            reply(
                    context,
                    "请打开微信中的舞萌玩家二维码，长按识别二维码，"
                            + "然后私聊发送：\n/bind mai SGWCMAID...\n"
                            + "二维码内容属于敏感凭证，请勿在群聊发送。"
            );
            return;
        }

        messageSender.reply(context, "正在验证舞萌二维码，请稍候……")
                .exceptionally(error -> null);
        maimaiBindingService.bind(context.userId(), parts[1])
                .thenCompose(ignored -> messageSender.reply(
                        context,
                        "舞萌账号绑定成功，已保存加密玩家标识。"
                ))
                .exceptionally(error -> {
                    Throwable cause = unwrap(error);
                    log.warn(
                            "绑定舞萌账号失败: userId={}, error={}",
                            context.userId(),
                            cause.toString()
                    );
                    reply(context, friendlyMaimaiMessage(cause));
                    return null;
                });
    }

    private String friendlyMaimaiMessage(Throwable error) {
        if (error instanceof MaimaiAdapterException adapterError) {
            if (adapterError.statusCode() == 0) {
                return "舞萌数据适配器尚未启动，请联系管理员。";
            }
            if (adapterError.statusCode() == 400
                    || adapterError.statusCode() == 422) {
                return "二维码无效或已过期，请重新生成玩家二维码。";
            }
            return "舞萌账号验证失败，请稍后重试。";
        }
        if (error instanceof IllegalStateException
                || error instanceof IllegalArgumentException) {
            return error.getMessage();
        }
        return "舞萌账号绑定失败，请稍后重试。";
    }

    private void showStatus(CommandContext context) {
        try {
            LxnsOAuthBindingService.BindingStatus status =
                    lxnsBindingService.status(context.userId());
            if (!status.bound()) {
                reply(context, "尚未绑定落雪，请执行 /bind lxns");
                return;
            }
            String suffix = status.username().isBlank()
                    ? ""
                    : "：" + status.username();
            reply(context, "落雪 OAuth 已绑定" + suffix);
        } catch (Exception error) {
            log.warn("查询落雪绑定状态失败: userId={}",
                    context.userId(), error);
            reply(context, "查询落雪绑定状态失败，请稍后重试。");
        }
    }

    private void beginAuthorization(CommandContext context) {
        try {
            LxnsOAuthBindingService.BindingStatus status =
                    lxnsBindingService.status(context.userId());
            String url = lxnsBindingService.beginAuthorization(context.userId());
            String prefix = status.bound()
                    ? "你已经绑定过落雪，本次授权成功后将替换旧授权。\n"
                    : "";
            reply(
                    context,
                    prefix
                            + "请在 10 分钟内打开以下链接完成授权：\n"
                            + url
                            + "\n授权后复制网页显示的授权码，并私聊发送：\n"
                            + "/bind lxns 授权码"
            );
        } catch (Exception error) {
            log.warn("创建落雪 OAuth 授权会话失败: userId={}",
                    context.userId(), error);
            reply(context, friendlyMessage(error));
        }
    }

    private void completeAuthorization(
            CommandContext context,
            String authorizationCode
    ) {
        lxnsBindingService.completeAuthorization(
                        context.userId(),
                        authorizationCode
                )
                .thenCompose(result -> messageSender.reply(
                        context,
                        "落雪绑定成功：" + result.username()
                ))
                .exceptionally(error -> {
                    Throwable cause = unwrap(error);
                    log.warn("完成落雪 OAuth 绑定失败: userId={}, error={}",
                            context.userId(), cause.toString());
                    reply(context, friendlyMessage(cause));
                    return null;
                });
    }

    private String friendlyMessage(Throwable error) {
        if (error instanceof LxnsOAuthException oauthError) {
            return switch (oauthError.statusCode()) {
                case 400 -> "授权码无效或已过期，请重新执行 /bind lxns。";
                case 401 -> "落雪 OAuth 应用配置无效，请联系管理员。";
                case 429 -> "落雪请求过于频繁，请稍后重试。";
                default -> "落雪授权失败，请稍后重试。";
            };
        }
        if (error instanceof IllegalStateException
                || error instanceof IllegalArgumentException) {
            return error.getMessage();
        }
        return "落雪授权失败，请稍后重试。";
    }

    private Throwable unwrap(Throwable error) {
        Throwable current = error;
        while (current instanceof CompletionException
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private void reply(CommandContext context, String message) {
        messageSender.reply(context, message)
                .exceptionally(error -> {
                    log.warn("回复落雪绑定消息失败", error);
                    return null;
                });
    }
}
