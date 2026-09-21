package per.kokabot.provider.divingfish;

import org.junit.jupiter.api.Test;
import per.kokabot.maimai.model.B50Result;
import per.kokabot.provider.divingfish.response.DivingFishB50ResponseVO;
import per.kokabot.provider.divingfish.oauth.DivingFishOAuthException;
import per.kokabot.service.DivingFishOAuthService;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DivingFishScoreProviderTest {

    @Test
    void convertsDivingFishResponseToCommonModel() {
        DivingFishClient client = mock(DivingFishClient.class);
        DivingFishOAuthService oauthService = mock(
                DivingFishOAuthService.class
        );
        DivingFishB50ResponseVO.Score score =
                new DivingFishB50ResponseVO.Score(
                        "834",
                        "测试歌曲",
                        "DX",
                        "Master",
                        "14+",
                        14.8,
                        100.5000,
                        3000,
                        320,
                        "ap",
                        "fsd"
                );
        DivingFishB50ResponseVO response =
                new DivingFishB50ResponseVO(
                        "水鱼玩家",
                        15000,
                        new DivingFishB50ResponseVO.Charts(
                                List.of(score),
                                List.of(score)
                        )
                );

        when(client.queryB50(123L))
                .thenReturn(CompletableFuture.completedFuture(response));
        when(oauthService.isConfigured()).thenReturn(false);

        B50Result result =
                new DivingFishScoreProvider(client, oauthService)
                        .queryB50(123L)
                        .join();

        assertEquals("水鱼玩家", result.playerName());
        assertEquals(15000, result.rating());
        assertEquals("834", result.best35().get(0).songId());
        assertEquals("Master", result.best15().get(0).difficulty());
        assertEquals(320, result.best15().get(0).rating());
        assertEquals(14.8, result.best15().get(0).chartConstant());
        assertEquals("ap", result.best15().get(0).fullCombo());
    }

    @Test
    void usesAuthorizedOAuthResultWhenConfigured() {
        DivingFishClient client = mock(DivingFishClient.class);
        DivingFishOAuthService oauthService = mock(
                DivingFishOAuthService.class
        );
        B50Result expected = new B50Result(
                "OAuth 玩家",
                16000,
                List.of(),
                List.of()
        );
        when(oauthService.isConfigured()).thenReturn(true);
        when(oauthService.queryB50(123L))
                .thenReturn(CompletableFuture.completedFuture(expected));

        B50Result result = new DivingFishScoreProvider(client, oauthService)
                .queryB50(123L)
                .join();

        assertEquals(expected, result);
        org.mockito.Mockito.verifyNoInteractions(client);
    }

    @Test
    void fallsBackToPublicQueryWhenUserHasNotAuthorizedOAuth() {
        DivingFishClient client = mock(DivingFishClient.class);
        DivingFishOAuthService oauthService = mock(
                DivingFishOAuthService.class
        );
        DivingFishB50ResponseVO response = new DivingFishB50ResponseVO(
                "公开玩家",
                10000,
                new DivingFishB50ResponseVO.Charts(List.of(), List.of())
        );
        when(oauthService.isConfigured()).thenReturn(true);
        when(oauthService.queryB50(123L)).thenReturn(
                CompletableFuture.failedFuture(
                        new DivingFishOAuthException(
                                400,
                                "consent_required",
                                "authorization required"
                        )
                )
        );
        when(client.queryB50(123L))
                .thenReturn(CompletableFuture.completedFuture(response));

        B50Result result = new DivingFishScoreProvider(client, oauthService)
                .queryB50(123L)
                .join();

        assertEquals("公开玩家", result.playerName());
    }
}
