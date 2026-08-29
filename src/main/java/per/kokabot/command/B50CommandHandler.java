package per.kokabot.command;

import org.springframework.stereotype.Component;
import per.kokabot.command.context.CommandContext;
import per.kokabot.onebot.OneBotMessageSender;

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

    private final OneBotMessageSender messageSender;

    public B50CommandHandler(OneBotMessageSender messageSender) {
        this.messageSender = messageSender;
    }

    @Override
    public String command() {
        return "b50";
    }

    @Override
    public void handle(CommandContext context) {
        System.out.println("用户 QQ：" + context.userId());
        System.out.println("用户昵称：" + context.nickname());
        System.out.println("命令参数：" + context.arguments());

        messageSender.reply(context, "B50 查询功能正在开发中")
                .exceptionally(error -> {
                    System.out.println("回复消息失败：" + error.getMessage());
                    return null;
                });
    }
}
