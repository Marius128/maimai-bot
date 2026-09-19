package per.kokabot.command;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import per.kokabot.command.context.CommandContext;
import per.kokabot.onebot.OneBotMessageSender;

@Component
public class HelpCommandHandler implements CommandHandler {

    private static final Logger log =
            LoggerFactory.getLogger(HelpCommandHandler.class);

    private static final String HELP_MESSAGE = """
            KokaBot 常用指令

            【成绩查询】
            /b50
            生成你的舞萌 DX Best 50 图片。

            /source
            查看当前 B50 数据源。
            /source fish
            切换为水鱼数据源。

            /source lxns
            切换为落雪数据源。

            【成绩更新】
            /update
            从已绑定的舞萌账号读取机台成绩，只把新增或提高的记录同步到落雪。
            快捷更新暂不包含 FC、FC+、AP、AP+ 和同步标志。

            【账号绑定（请私聊机器人）】
            /bind lxns
            绑定或重新授权落雪账号。

            /bind lxns status
            查看落雪绑定状态。

            /bind mai
            查看舞萌二维码绑定方法。

            /bind mai status
            查看舞萌账号绑定状态。

            /unbind lxns
            删除机器人保存的落雪授权。

            /unbind mai
            删除机器人保存的舞萌玩家标识。

            【帮助】
            /help
            显示本帮助。

            群聊使用指令时需要先 @机器人。
            授权码和 SGWCMAID 属于敏感信息，只能私聊发送。
            """;

    private final OneBotMessageSender messageSender;

    public HelpCommandHandler(OneBotMessageSender messageSender) {
        this.messageSender = messageSender;
    }

    @Override
    public String command() {
        return "help";
    }

    @Override
    public void handle(CommandContext context) {
        messageSender.reply(context, HELP_MESSAGE)
                .exceptionally(error -> {
                    log.warn("回复帮助消息失败", error);
                    return null;
                });
    }
}
