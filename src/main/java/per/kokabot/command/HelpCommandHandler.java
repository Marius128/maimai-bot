package per.kokabot.command;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import per.kokabot.command.context.CommandContext;
import per.kokabot.onebot.OneBotMessageSender;

import java.io.IOException;
import java.io.InputStream;

@Component
public class HelpCommandHandler implements CommandHandler {

    private static final Logger log =
            LoggerFactory.getLogger(HelpCommandHandler.class);
    private static final String HELP_IMAGE_PATH = "templates/help.png";

    private final OneBotMessageSender messageSender;
    private final byte[] helpImage;

    public HelpCommandHandler(OneBotMessageSender messageSender) {
        this.messageSender = messageSender;
        this.helpImage = loadHelpImage();
    }

    @Override
    public String command() {
        return "help";
    }

    @Override
    public void handle(CommandContext context) {
        messageSender.replyImage(context, helpImage)
                .exceptionally(error -> {
                    log.warn("回复帮助图片失败", error);
                    return null;
                });
    }

    private byte[] loadHelpImage() {
        ClassPathResource resource = new ClassPathResource(
                HELP_IMAGE_PATH
        );
        try (InputStream inputStream = resource.getInputStream()) {
            return inputStream.readAllBytes();
        } catch (IOException error) {
            throw new IllegalStateException(
                    "无法读取帮助图片：" + HELP_IMAGE_PATH,
                    error
            );
        }
    }
}
