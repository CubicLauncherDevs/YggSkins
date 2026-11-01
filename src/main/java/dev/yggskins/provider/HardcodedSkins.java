package dev.yggskins.provider;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yggskins.skin.SkinData;
import dev.yggskins.skin.SkinSource;
import dev.yggskins.skin.SkinVariant;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Lee skins predefinidas con firma ya incluida.
 */
public final class HardcodedSkins {

    private HardcodedSkins() {
    }

    public static Map<String, SkinData> parse(String json) {
        Map<String, SkinData> result = new HashMap<>();
        if (json == null || json.isBlank()) {
            return result;
        }
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) {
                return result;
            }
            JsonObject object = root.getAsJsonObject();
            JsonObject skins = object.has("skins") && object.get("skins").isJsonObject()
                    ? object.getAsJsonObject("skins") : object;
            for (Map.Entry<String, JsonElement> entry : skins.entrySet()) {
                if (!entry.getValue().isJsonObject()) {
                    continue;
                }
                JsonObject skin = entry.getValue().getAsJsonObject();
                if (!skin.has("value")) {
                    continue;
                }
                String value = skin.get("value").getAsString();
                String signature = skin.has("signature") && !skin.get("signature").isJsonNull()
                        ? skin.get("signature").getAsString() : null;
                SkinVariant variant = SkinVariant.parseOrDefault(
                        skin.has("variant") ? skin.get("variant").getAsString() : null, SkinVariant.CLASSIC);
                String name = entry.getKey().toLowerCase(Locale.ROOT);
                result.put(name, new SkinData(0L, value, signature, variant, SkinSource.HARDCODED, name,
                        System.currentTimeMillis() / 1000L));
            }
        } catch (RuntimeException e) {
            return result;
        }
        return result;
    }
}
