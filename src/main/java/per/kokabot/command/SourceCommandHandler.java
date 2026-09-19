package per.kokabot.command;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import per.kokabot.command.context.CommandContext;
import per.kokabot.onebot.OneBotMessageSender;
import per.kokabot.provider.MaimaiProviderType;
import per.kokabot.service.MaimaiProviderPreferenceService;

import java.util.Locale;

/**
 * @BelongsProject: maimai-bot
 * @BelongsPackage: per.kokabot.command
 * @Author: L
 * @CreateTime: 2026-08-29 18:47
 * @Description: TODO
 * @Version: 1.0
 */
@Component
public class SourceCommandHandler implements CommandHandler{

    private static final Logger log = LoggerFactory.getLogger(SourceCommandHandler.class);

    private final MaimaiProviderPreferenceService preferenceService;
    private final OneBotMessageSender messageSender;

    public SourceCommandHandler(MaimaiProviderPreferenceService preferenceService, OneBotMessageSender messageSender) {
        this.preferenceService = preferenceService;
        this.messageSender = messageSender;
    }

    @Override
    public void handle(CommandContext context) {
        String input = context.arguments()
                .trim()
                .toLowerCase(Locale.ROOT);

        try {
            if (input.isEmpty()) {
                showCurrentProvider(context);
                return;
            }

            MaimaiProviderType providerType =
                    parseProvider(input);

            preferenceService.setProvider(
                    context.userId(),
                    providerType
            );

            reply(context, "数据源已切换为：" + providerType.displayName());
        } catch (IllegalArgumentException error) {
            reply(context, "不支持的数据源，请使用：/source fish 或 /source lxns");
        } catch (Exception error) {
            log.error("切换用户数据源失败", error);
            reply(context, "切换数据源失败，请稍后重试");
        }
    }

    private void showCurrentProvider(CommandContext context) {
        MaimaiProviderType providerType = preferenceService.getProvider(context.userId());

        reply(
                context,
                "当前数据源：" + providerType.displayName()
        );
    }

    private MaimaiProviderType parseProvider(String input) {
        return switch (input) {
            case "fish", "water", "divingfish", "水鱼" ->
                    MaimaiProviderType.DIVING_FISH;

            case "lxns", "落雪" ->
                    MaimaiProviderType.LXNS;

            default -> throw new IllegalArgumentException(
                    "未知数据源：" + input
            );
        };
    }

    private void reply(CommandContext context, String message) {
        messageSender.reply(context, message)
                .exceptionally(error -> {
                    log.error("回复消息失败", error);
                    return null;
                });
    }

    @Override
    public String command() {
        return "source";
    }
}
