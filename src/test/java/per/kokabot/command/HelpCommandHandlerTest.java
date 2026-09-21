package per.kokabot.command;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import per.kokabot.command.context.CommandContext;
import per.kokabot.onebot.OneBotMessageSender;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HelpCommandHandlerTest {

    @Test
    void shouldReplyWithBundledHelpImage() {
        OneBotMessageSender messageSender = mock(
                OneBotMessageSender.class
        );
        when(messageSender.replyImage(any(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));
        HelpCommandHandler handler = new HelpCommandHandler(messageSender);
        CommandContext context = new CommandContext(
                805670312L,
                null,
                "private",
                1L,
                "迪亚",
                "help",
                ""
        );

        handler.handle(context);

        ArgumentCaptor<byte[]> imageCaptor =
                ArgumentCaptor.forClass(byte[].class);
        verify(messageSender).replyImage(
                eq(context),
                imageCaptor.capture()
        );
        byte[] image = imageCaptor.getValue();
        assertThat(image).isNotEmpty();
        assertThat(image[0] & 0xff).isEqualTo(0x89);
        assertThat(image[1] & 0xff).isEqualTo(0x50);
        assertThat(image[2] & 0xff).isEqualTo(0x4e);
        assertThat(image[3] & 0xff).isEqualTo(0x47);
    }
}
