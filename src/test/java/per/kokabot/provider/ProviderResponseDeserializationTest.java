package per.kokabot.provider;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import per.kokabot.provider.divingfish.response.DivingFishB50ResponseVO;
import per.kokabot.provider.lxns.response.LxnsApiResponseVO;
import per.kokabot.provider.lxns.response.LxnsPlayerVO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderResponseDeserializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void readsDivingFishB50Json() throws Exception {
        String json = """
                {
                  "nickname": "水鱼玩家",
                  "rating": 15000,
                  "charts": {
                    "sd": [{
                      "song_id": 834,
                      "title": "测试歌曲",
                      "type": "DX",
                      "level_label": "Master",
                      "level": "14+",
                      "ds": 14.8,
                      "achievements": 100.5,
                      "dxScore": 3000,
                      "ra": 320,
                      "fc": "ap",
                      "fs": "fsd",
                      "unknown_field": true
                    }],
                    "dx": []
                  }
                }
                """;

        DivingFishB50ResponseVO response = objectMapper.readValue(
                json,
                DivingFishB50ResponseVO.class
        );

        assertEquals("水鱼玩家", response.nickname());
        assertEquals("834", response.charts().sd().get(0).songId());
        assertEquals(3000, response.charts().sd().get(0).dxScore());
        assertEquals(14.8, response.charts().sd().get(0).ds());
        assertEquals("ap", response.charts().sd().get(0).fc());
    }

    @Test
    void readsLxnsEnvelopeJson() throws Exception {
        String json = """
                {
                  "success": true,
                  "code": 200,
                  "data": {
                    "name": "落雪玩家",
                    "rating": 15100,
                    "friend_code": 123456789,
                    "unknown_field": "ignored"
                  }
                }
                """;

        JavaType type = objectMapper.getTypeFactory()
                .constructParametricType(
                        LxnsApiResponseVO.class,
                        LxnsPlayerVO.class
                );
        LxnsApiResponseVO<LxnsPlayerVO> response =
                objectMapper.readValue(json, type);

        assertTrue(response.success());
        assertEquals("落雪玩家", response.data().name());
        assertEquals(123456789L, response.data().friendCode());
    }
}
