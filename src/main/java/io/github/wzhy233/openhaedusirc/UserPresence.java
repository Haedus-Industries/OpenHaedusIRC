package io.github.wzhy233.openhaedusirc;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;
import java.util.UUID;

public record UserPresence(String mcName, String uuid, String skinName, boolean customSkin, boolean cape, String model) {
    public static final UserPresence EMPTY = new UserPresence("", "", "", false, true, "auto");

    public UserPresence {
        if (mcName == null || !mcName.matches("[A-Za-z0-9_]{0,16}") || uuid == null
                || skinName == null || !skinName.isEmpty() && !skinName.matches("[A-Za-z0-9_]{3,16}")
                || !customSkin && !skinName.isEmpty()
                || !"auto".equals(model) && !"slim".equals(model) && !"wide".equals(model)) {
            throw new IllegalArgumentException("Invalid presence");
        }
        if (!uuid.isEmpty()) {
            if (!uuid.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
                throw new IllegalArgumentException("Invalid player UUID");
            }
            uuid = UUID.fromString(uuid).toString();
        }
    }

    public static UserPresence fromJson(JsonNode json) {
        if (json == null || !json.isObject()) throw new IllegalArgumentException("Invalid presence object");
        return new UserPresence(string(json, "mcName"), string(json, "uuid"), string(json, "skin-name"),
                bool(json, "customSkin"), bool(json, "cape"), string(json, "model"));
    }

    public Map<String, Object> toMap() {
        return Map.of("mcName", mcName, "uuid", uuid, "skin-name", skinName,
                "customSkin", customSkin, "cape", cape, "model", model);
    }

    private static String string(JsonNode json, String field) {
        JsonNode value = json.get(field);
        if (value == null || !value.isTextual()) throw new IllegalArgumentException("Invalid presence field: " + field);
        return value.asText();
    }

    private static boolean bool(JsonNode json, String field) {
        JsonNode value = json.get(field);
        if (value == null || !value.isBoolean()) throw new IllegalArgumentException("Invalid presence field: " + field);
        return value.asBoolean();
    }
}
