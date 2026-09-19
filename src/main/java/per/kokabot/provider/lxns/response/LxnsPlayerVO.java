package per.kokabot.provider.lxns.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LxnsPlayerVO(
        String name,
        int rating,
        @JsonProperty("friend_code") long friendCode
) {
}
