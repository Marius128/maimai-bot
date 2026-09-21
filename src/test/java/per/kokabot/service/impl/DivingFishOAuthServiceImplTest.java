package per.kokabot.service.impl;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import per.kokabot.maimai.model.B50Result;
import per.kokabot.provider.divingfish.oauth.DivingFishOAuthClient;
import per.kokabot.provider.divingfish.response.DivingFishB50ResponseVO;
import per.kokabot.provider.divingfish.response.DivingFishRecordsResponseVO;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DivingFishOAuthServiceImplTest {

    @Test
    void createsMaskedDeviceAuthorizationForStableSubjectReference()
            throws Exception {
        DivingFishOAuthClient client = mock(DivingFishOAuthClient.class);
        when(client.clientId()).thenReturn("client-id");
        when(client.beginAuthorization(anyString(), anyString()))
                .thenReturn(new DivingFishOAuthClient.DeviceAuthorization(
                        "https://auth.example/device?user_code=ABCD",
                        600
                ));
        DivingFishOAuthServiceImpl service = new DivingFishOAuthServiceImpl(
                client,
                Runnable::run
        );

        var result = service.beginAuthorization(2803515635L);

        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> label = ArgumentCaptor.forClass(String.class);
        verify(client).beginAuthorization(subject.capture(), label.capture());
        String expectedSubject = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(
                        "client-id:2803515635"
                                .getBytes(StandardCharsets.UTF_8)
                )
        );
        assertThat(subject.getValue()).isEqualTo(expectedSubject);
        assertThat(label.getValue()).isEqualTo("QQ 28****35");
        assertThat(result.expiresIn()).isEqualTo(600);
    }

    @Test
    void buildsB35AndB15FromAuthenticatedFilteredRecords() {
        DivingFishOAuthClient client = mock(DivingFishOAuthClient.class);
        when(client.clientId()).thenReturn("client-id");
        when(client.exchangeToken(anyString()))
                .thenReturn(new DivingFishOAuthClient.AccessToken(
                        "access-token",
                        300
                ));
        DivingFishB50ResponseVO.Score oldLow = score("1", 100);
        DivingFishB50ResponseVO.Score oldHigh = score("2", 300);
        DivingFishB50ResponseVO.Score current = score("3", 250);
        when(client.records("access-token", false)).thenReturn(
                new DivingFishRecordsResponseVO(
                        "OAuth 玩家",
                        12345,
                        List.of(oldLow, oldHigh)
                )
        );
        when(client.records("access-token", true)).thenReturn(
                new DivingFishRecordsResponseVO(
                        "OAuth 玩家",
                        12345,
                        List.of(current)
                )
        );
        DivingFishOAuthServiceImpl service = new DivingFishOAuthServiceImpl(
                client,
                Runnable::run
        );

        B50Result first = service.queryB50(123L).join();
        B50Result second = service.queryB50(123L).join();

        assertThat(first.playerName()).isEqualTo("OAuth 玩家");
        assertThat(first.rating()).isEqualTo(12345);
        assertThat(first.best35())
                .extracting(score -> score.songId())
                .containsExactly("2", "1");
        assertThat(first.best15())
                .extracting(score -> score.songId())
                .containsExactly("3");
        assertThat(second.best15()).hasSize(1);
        verify(client, times(1)).exchangeToken(anyString());
    }

    private DivingFishB50ResponseVO.Score score(String id, int rating) {
        return new DivingFishB50ResponseVO.Score(
                id,
                "歌曲 " + id,
                "DX",
                "Master",
                "14",
                14.0,
                100.0,
                1000,
                rating,
                "fc",
                "fs"
        );
    }
}
