package per.kokabot.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MaimaiQuickSyncServiceImplTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void skipsIdenticalAndWorseScores() throws Exception {
        JsonNode target = score(100, "dx", 3, 100.5, 900000, 1, 2);
        JsonNode identical = score(100, "dx", 3, 100.5, 900000, 1, 2);
        JsonNode worse = score(100, "dx", 3, 99.0, 800000, null, null);

        assertThat(MaimaiQuickSyncServiceImpl.changedScores(
                List.of(identical, worse),
                List.of(target)
        )).isEmpty();
    }

    @Test
    void uploadsNewScoresAndMergesExistingBadges() throws Exception {
        JsonNode target = score(100, "standard", 4, 100.0, 800000, 3, 4);
        JsonNode improved = score(100, "standard", 4, 100.6, 900000, null, null);
        JsonNode newScore = score(200, "dx", 2, 98.0, 700000, null, null);

        List<JsonNode> changed = MaimaiQuickSyncServiceImpl.changedScores(
                List.of(improved, newScore),
                List.of(target)
        );

        assertThat(changed).hasSize(2);
        assertThat(changed.get(0).path("achievements").asDouble())
                .isEqualTo(100.6);
        assertThat(changed.get(0).path("dx_score").asInt())
                .isEqualTo(900000);
        assertThat(changed.get(0).path("fc").asInt()).isEqualTo(3);
        assertThat(changed.get(0).path("fs").asInt()).isEqualTo(4);
        assertThat(changed.get(1).path("id").asInt()).isEqualTo(200);
    }

    private JsonNode score(
            int id,
            String type,
            int levelIndex,
            double achievements,
            int dxScore,
            Integer fc,
            Integer fs
    ) throws Exception {
        String fcJson = fc == null ? "null" : fc.toString();
        String fsJson = fs == null ? "null" : fs.toString();
        return objectMapper.readTree("""
                {
                  "id": %d,
                  "level": "14",
                  "level_index": %d,
                  "achievements": %s,
                  "fc": %s,
                  "fs": %s,
                  "dx_score": %d,
                  "dx_rating": 0,
                  "play_count": null,
                  "play_time": null,
                  "rate": 0,
                  "type": "%s"
                }
                """.formatted(
                id,
                levelIndex,
                achievements,
                fcJson,
                fsJson,
                dxScore,
                type
        ));
    }
}
