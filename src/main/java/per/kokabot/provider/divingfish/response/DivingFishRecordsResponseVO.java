package per.kokabot.provider.divingfish.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DivingFishRecordsResponseVO(
        String nickname,
        int rating,
        List<DivingFishB50ResponseVO.Score> records
) {
}
