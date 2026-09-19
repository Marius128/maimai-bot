package per.kokabot.provider.lxns.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LxnsApiResponseVO<T>(
        boolean success,
        int code,
        String message,
        T data
) {
}
