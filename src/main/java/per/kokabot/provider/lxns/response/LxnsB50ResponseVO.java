package per.kokabot.provider.lxns.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LxnsB50ResponseVO(
        @JsonProperty("standard_total") int standardTotal,
        @JsonProperty("dx_total") int dxTotal,
        List<Score> standard,
        List<Score> dx
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Score(
            int id,
            @JsonProperty("song_name") String songName,
            String level,
            @JsonProperty("level_index") int levelIndex,
            double achievements,
            @JsonProperty("dx_score") int dxScore,
            @JsonProperty("dx_star") int dxStar,
            @JsonProperty("dx_rating") double dxRating,
            String fc,
            String fs,
            String type
    ) {
    }
}
