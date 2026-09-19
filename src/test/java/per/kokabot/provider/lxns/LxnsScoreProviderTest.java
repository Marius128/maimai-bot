package per.kokabot.provider.lxns;

import org.junit.jupiter.api.Test;
import per.kokabot.maimai.model.B50Result;
import per.kokabot.provider.lxns.response.LxnsB50DataVO;
import per.kokabot.provider.lxns.response.LxnsB50ResponseVO;
import per.kokabot.provider.lxns.response.LxnsPlayerVO;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LxnsScoreProviderTest {

    @Test
    void convertsLxnsResponseToCommonModel() {
        LxnsClient client = mock(LxnsClient.class);
        LxnsB50ResponseVO.Score score =
                new LxnsB50ResponseVO.Score(
                        834,
                        "测试歌曲",
                        "14+",
                        4,
                        100.5000,
                        3000,
                        5,
                        319.99,
                        "app",
                        "fsdp",
                        "standard"
                );
        LxnsB50DataVO response = new LxnsB50DataVO(
                new LxnsPlayerVO("落雪玩家", 15100, 123456789L),
                new LxnsB50ResponseVO(
                        10000,
                        5000,
                        List.of(score),
                        List.of(score)
                )
        );

        when(client.queryB50(123L))
                .thenReturn(CompletableFuture.completedFuture(response));

        B50Result result =
                new LxnsScoreProvider(client).queryB50(123L).join();

        assertEquals("落雪玩家", result.playerName());
        assertEquals(15100, result.rating());
        assertEquals("834", result.best35().get(0).songId());
        assertEquals("SD", result.best35().get(0).chartType());
        assertEquals("Re:Master", result.best15().get(0).difficulty());
        assertEquals(319, result.best15().get(0).rating());
        assertEquals(5, result.best15().get(0).dxStar());
        assertEquals("fsdp", result.best15().get(0).fullSync());
    }
}
