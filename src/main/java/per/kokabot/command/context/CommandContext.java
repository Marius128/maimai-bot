package per.kokabot.command.context;

/**
 * @BelongsProject: maimai-bot
 * @BelongsPackage: per.kokabot.command.context
 * @Author: L
 * @CreateTime: 2026-08-29 17:00
 * @Description: TODO
 * @Version: 1.0
 */
public record CommandContext(long userId,
                             Long groupId,
                             String messageType,
                             long messageId,
                             String nickname,
                             String commandName,
                             String arguments) {
}
