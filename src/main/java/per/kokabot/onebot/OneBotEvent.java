package per.kokabot.onebot;

import com.fasterxml.jackson.databind.JsonNode;

public record OneBotEvent(JsonNode payload) {
}
