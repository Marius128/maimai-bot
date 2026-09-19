package per.kokabot.command;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import per.kokabot.command.context.CommandContext;
import per.kokabot.maimai.adapter.MaimaiAdapterException;
import per.kokabot.onebot.OneBotMessageSender;
import per.kokabot.provider.lxns.LxnsApiException;
import per.kokabot.provider.lxns.oauth.LxnsOAuthException;
import per.kokabot.service.MaimaiQuickSyncService;

import java.util.concurrent.CompletionException;

@Component
public class UpdateCommandHandler implements CommandHandler {

    private static final Logger log =
            LoggerFactory.getLogger(UpdateCommandHandler.class);

    private final MaimaiQuickSyncService syncService;
    private final OneBotMessageSender messageSender;

    public UpdateCommandHandler(
            MaimaiQuickSyncService syncService,
            OneBotMessageSender messageSender
    ) {
        this.syncService = syncService;
        this.messageSender = messageSender;
    }

    @Override
    public String command() {
        return "update";
    }

    @Override
    public void handle(CommandContext context) {
        messageSender.reply(context, "正在读取机台成绩并同步到落雪……")
                .thenCompose(ignored -> syncService.sync(context.userId()))
                .handle((result, error) -> {
                    if (error == null) {
                        if (result.changed() == 0) {
                            return "更新完成：共读取 " + result.fetched()
                                    + " 条成绩，没有发现新纪录。";
                        }
                        return "更新完成：共读取 " + result.fetched()
                                + " 条成绩，向落雪写入 " + result.changed()
                                + " 条新纪录。\n"
                                + "快捷更新不包含 FC、FC+、AP、AP+ 和同步标志。";
                    }
                    Throwable cause = unwrap(error);
                    log.warn(
                            "快捷更新失败: userId={}, error={}",
                            context.userId(),
                            cause.toString()
                    );
                    return friendlyError(cause);
                })
                .thenCompose(message -> messageSender.reply(context, message))
                .exceptionally(error -> {
                    log.warn("回复快捷更新消息失败", error);
                    return null;
                });
    }

    private String friendlyError(Throwable error) {
        if (error instanceof IllegalStateException
                || error instanceof IllegalArgumentException) {
            return error.getMessage();
        }
        if (error instanceof LxnsOAuthException oauthError) {
            return switch (oauthError.statusCode()) {
                case 400, 401 -> "落雪授权已失效，请重新私聊发送 /bind lxns。";
                case 403 -> "落雪授权缺少写入玩家成绩的权限，请重新绑定。";
                case 429 -> "落雪请求过于频繁，请稍后再试。";
                default -> "落雪暂时无法接收成绩，请稍后再试。";
            };
        }
        if (error instanceof LxnsApiException apiError) {
            return switch (apiError.statusCode()) {
                case 400 -> "成绩数据与落雪接口不兼容，请联系管理员更新机器人。";
                case 401 -> "落雪授权已失效，请重新私聊发送 /bind lxns。";
                case 403 -> "落雪授权缺少写入玩家成绩的权限，请重新绑定。";
                case 429 -> "落雪请求过于频繁，请稍后再试。";
                default -> "落雪暂时无法接收成绩，请稍后再试。";
            };
        }
        if (error instanceof MaimaiAdapterException adapterError) {
            if (adapterError.statusCode() == 0) {
                return "舞萌适配器未启动，请联系管理员。";
            }
            if (adapterError.details().contains("TitleServerBlockedError")) {
                return "当前网络被舞萌机台服务器拒绝访问，请联系管理员检查网络或配置 HTTP 代理。";
            }
            if (adapterError.details().contains("TitleServerNetworkError")) {
                return "暂时无法连接舞萌机台服务器，请稍后再试。";
            }
            if (adapterError.details().contains("ArcadeIdentifierError")
                    || adapterError.details().contains(
                    "InvalidPlayerIdentifierError"
            )) {
                return "保存的舞萌玩家标识无效，请重新私聊发送 /bind mai。";
            }
            return "读取舞萌成绩失败，二维码绑定可能已失效，请重新私聊发送 /bind mai。";
        }
        return "成绩更新失败，请稍后重试。";
    }

    private Throwable unwrap(Throwable error) {
        Throwable current = error;
        while (current instanceof CompletionException
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
