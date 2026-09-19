package per.kokabot.provider.lxns;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class LxnsUserScoreClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldBuildOnlyFieldsAcceptedByLxnsUploadApi() throws Exception {
        JsonNode source = objectMapper.readTree("""
                {
                  "id": 834,
                  "type": "standard",
                  "level": "14+",
                  "level_index": 4,
                  "achievements": 100.5,
                  "fc": null,
                  "fs": null,
                  "dx_score": 1234,
                  "dx_rating": 321,
                  "play_count": null,
                  "play_time": null,
                  "rate": 0
                }
                """);

        ObjectNode upload = LxnsUserScoreClient.toUploadScore(
                objectMapper,
                source
        );

        assertThat(upload.properties())
                .extracting(java.util.Map.Entry::getKey)
                .containsExactlyInAnyOrderElementsOf(Set.of(
                        "id",
                        "type",
                        "level_index",
                        "achievements",
                        "fc",
                        "fs",
                        "dx_score",
                        "play_time"
                ));
        assertThat(upload.has("level")).isFalse();
        assertThat(upload.has("dx_rating")).isFalse();
        assertThat(upload.has("rate")).isFalse();
        assertThat(upload.has("play_count")).isFalse();
    }

    @Test
    void shouldConvertMaimaiPyBadgeEnumsToLxnsNames() throws Exception {
        JsonNode source = objectMapper.readTree("""
                {
                  "id": 834,
                  "type": "DX",
                  "level_index": 3,
                  "achievements": 101.0,
                  "fc": 0,
                  "fs": 4,
                  "dx_score": 2000
                }
                """);

        ObjectNode upload = LxnsUserScoreClient.toUploadScore(
                objectMapper,
                source
        );

        assertThat(upload.path("type").asText()).isEqualTo("dx");
        assertThat(upload.path("fc").asText()).isEqualTo("app");
        assertThat(upload.path("fs").asText()).isEqualTo("fsdp");
    }
}
