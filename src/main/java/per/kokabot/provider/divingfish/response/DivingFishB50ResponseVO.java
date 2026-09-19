package per.kokabot.provider.divingfish.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * @BelongsProject: maimai-bot
 * @BelongsPackage: per.kokabot.provider.divingfish.response
 * @Author: L
 * @CreateTime: 2026-09-19 10:02
 * @Description: TODO
 * @Version: 1.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DivingFishB50ResponseVO(
        String nickname,
        int rating,
        Charts charts
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Charts(
            List<Score> sd,
            List<Score> dx
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Score(
            @JsonProperty("song_id")
            String songId,

            String title,
            String type,

            @JsonProperty("level_label")
            String levelLabel,

            String level,
            double ds,
            double achievements,
            int dxScore,
            int ra,
            String fc,
            String fs
    ) {
    }
}
