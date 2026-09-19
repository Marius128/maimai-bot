package per.kokabot.provider.divingfish;

import org.junit.jupiter.api.Test;
import per.kokabot.maimai.model.B50Result;
import per.kokabot.provider.divingfish.response.DivingFishB50ResponseVO;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DivingFishScoreProviderTest {

    @Test
    void convertsDivingFishResponseToCommonModel() {
        DivingFishClient client = mock(DivingFishClient.class);
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

        B50Result result =
                new DivingFishScoreProvider(client).queryB50(123L).join();

        assertEquals("水鱼玩家", result.playerName());
        assertEquals(15000, result.rating());
        assertEquals("834", result.best35().get(0).songId());
        assertEquals("Master", result.best15().get(0).difficulty());
        assertEquals(320, result.best15().get(0).rating());
        assertEquals(14.8, result.best15().get(0).chartConstant());
        assertEquals("ap", result.best15().get(0).fullCombo());
    }
}
