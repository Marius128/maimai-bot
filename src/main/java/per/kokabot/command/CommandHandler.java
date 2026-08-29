package per.kokabot.command;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * @BelongsProject: maimai-bot
 * @BelongsPackage: per.kokabot.command
 * @Author: L
 * @CreateTime: 2026-08-29 15:22
 * @Description: TODO
 * @Version: 1.0
 */
public interface CommandHandler {
    String command();

    void handle(JsonNode payload, String arguments);
}
