package tytoo.grapheneui.internal.nativeui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.resources.Identifier;

import java.util.UUID;

final class GrapheneNativeSlotJson {
    private GrapheneNativeSlotJson() {
    }

    static JsonObject object(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return null;
        }

        return element.getAsJsonObject();
    }

    static String stringValue(JsonObject object, String defaultValue, String... keys) {
        JsonElement element = find(object, keys);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return defaultValue;
        }

        try {
            return element.getAsString();
        } catch (RuntimeException ignored) {
            return defaultValue;
        }
    }

    static boolean booleanValue(JsonObject object, boolean defaultValue, String... keys) {
        JsonElement element = find(object, keys);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return defaultValue;
        }

        try {
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            if (primitive.isBoolean()) {
                return primitive.getAsBoolean();
            }

            String value = primitive.getAsString();
            if ("true".equalsIgnoreCase(value)) {
                return true;
            }

            if ("false".equalsIgnoreCase(value)) {
                return false;
            }
        } catch (RuntimeException ignored) {
            return defaultValue;
        }

        return defaultValue;
    }

    static int intValue(JsonObject object, int defaultValue, String... keys) {
        JsonElement element = find(object, keys);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return defaultValue;
        }

        try {
            return element.getAsInt();
        } catch (RuntimeException ignored) {
            return defaultValue;
        }
    }

    static float floatValue(JsonObject object, float defaultValue, String... keys) {
        JsonElement element = find(object, keys);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return defaultValue;
        }

        try {
            return element.getAsFloat();
        } catch (RuntimeException ignored) {
            return defaultValue;
        }
    }

    static Identifier identifierValue(JsonObject object, String... keys) {
        String value = stringValue(object, null, keys);
        return value == null || value.isBlank() ? null : Identifier.tryParse(value);
    }

    static UUID uuidValue(JsonObject object, String... keys) {
        String value = stringValue(object, null, keys);
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    static int colorValue(JsonObject object, int defaultValue, String... keys) {
        JsonElement element = find(object, keys);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return defaultValue;
        }

        try {
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            if (primitive.isNumber()) {
                return primitive.getAsInt();
            }

            String value = primitive.getAsString().trim();
            if (value.startsWith("#")) {
                long parsed = Long.parseLong(value.substring(1), 16);
                return (int) (value.length() == 7 ? parsed | 0xFF000000L : parsed);
            }

            if (value.startsWith("0x") || value.startsWith("0X")) {
                return (int) Long.parseLong(value.substring(2), 16);
            }

            return Integer.parseInt(value);
        } catch (RuntimeException ignored) {
            return defaultValue;
        }
    }

    private static JsonElement find(JsonObject object, String... keys) {
        if (object == null || keys == null) {
            return null;
        }

        for (String key : keys) {
            if (key != null && object.has(key)) {
                return object.get(key);
            }
        }

        return null;
    }
}
