package per.kokabot.onebot;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import per.kokabot.command.context.CommandContext;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OneBotMessageSenderTest {

    @Test
    void shouldIncludeSourceGroupWhenReplyingToTemporaryPrivateSession() {
        OneBotClient client = mock(OneBotClient.class);
        when(client.callAction(eq("send_private_msg"), org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn(CompletableFuture.completedFuture(null));
        OneBotMessageSender sender = new OneBotMessageSender(client);
        CommandContext context = new CommandContext(
                1378953498L,
                123456789L,
                "private",
                1L,
                "tester",
                "help",
                ""
        );

        sender.reply(context, "reply");

        ArgumentCaptor<Map<String, Object>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(client).callAction(eq("send_private_msg"), paramsCaptor.capture());
        assertThat(paramsCaptor.getValue())
                .containsEntry("user_id", 1378953498L)
                .containsEntry("group_id", 123456789L)
                .containsEntry("message", "reply");
    }

    @Test
    void shouldNotIncludeGroupWhenReplyingToFriendPrivateSession() {
        OneBotClient client = mock(OneBotClient.class);
        when(client.callAction(eq("send_private_msg"), org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn(CompletableFuture.completedFuture(null));
        OneBotMessageSender sender = new OneBotMessageSender(client);
        CommandContext context = new CommandContext(
                1378953498L,
                null,
                "private",
                1L,
                "tester",
                "help",
                ""
        );

        sender.reply(context, "reply");

        ArgumentCaptor<Map<String, Object>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(client).callAction(eq("send_private_msg"), paramsCaptor.capture());
        assertThat(paramsCaptor.getValue())
                .containsEntry("user_id", 1378953498L)
                .containsEntry("message", "reply")
                .doesNotContainKey("group_id");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldBuildAGroupMentionSegment() {
        OneBotClient client = mock(OneBotClient.class);
        when(client.callAction(
                eq("send_group_msg"),
                org.mockito.ArgumentMatchers.anyMap()
        )).thenReturn(CompletableFuture.completedFuture(null));
        OneBotMessageSender sender = new OneBotMessageSender(client);

        sender.sendGroupWithMention(
                123456789L,
                "汇报人：",
                1378953498L,
                "\n汇报时间：2026-09-21 12:00:00"
        );

        ArgumentCaptor<Map<String, Object>> paramsCaptor =
                ArgumentCaptor.forClass(Map.class);
        verify(client).callAction(
                eq("send_group_msg"),
                paramsCaptor.capture()
        );
        assertThat(paramsCaptor.getValue())
                .containsEntry("group_id", 123456789L);
        List<Map<String, Object>> message = (List<Map<String, Object>>)
                paramsCaptor.getValue().get("message");
        assertThat(message).hasSize(3);
        assertThat(message.get(1)).containsEntry("type", "at");
        assertThat((Map<String, Object>) message.get(1).get("data"))
                .containsEntry("qq", "1378953498");
    }
}
