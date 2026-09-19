package per.kokabot.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import per.kokabot.command.context.CommandContext;
import per.kokabot.onebot.OneBotEvent;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommandDispatcherTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldReadSourceGroupFromSnowLumaTemporaryPrivateEvent() throws Exception {
        CommandHandler handler = mock(CommandHandler.class);
        when(handler.command()).thenReturn("help");
        CommandDispatcher dispatcher = new CommandDispatcher(List.of(handler));
        OneBotEvent event = new OneBotEvent(objectMapper.readTree("""
                {
                  "post_type": "message",
                  "message_type": "private",
                  "sub_type": "group",
                  "message_id": 10,
                  "user_id": 1378953498,
                  "raw_message": "/help",
                  "message": [{"type":"text","data":{"text":"/help"}}],
                  "sender": {
                    "nickname": "tester",
                    "group_id": 123456789
                  }
                }
                """));

        dispatcher.dispatch(event);

        ArgumentCaptor<CommandContext> contextCaptor = ArgumentCaptor.forClass(CommandContext.class);
        verify(handler).handle(contextCaptor.capture());
        assertThat(contextCaptor.getValue().groupId()).isEqualTo(123456789L);
    }
}
