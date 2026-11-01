package dev.yggskins.skin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Datos extraidos de una propiedad "textures" firmada.
 */
public record TextureInfo(String url, SkinVariant variant, String capeUrl) {

    public static TextureInfo parse(String base64Value) {
        if (base64Value == null || base64Value.isBlank()) {
            return null;
        }
        try {
            byte[] decoded = decode(base64Value);
            String json = new String(decoded, StandardCharsets.UTF_8);
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) {
                return null;
            }
            JsonObject textures = root.getAsJsonObject().getAsJsonObject("textures");
            if (textures == null) {
                return null;
            }
            JsonObject skin = textures.getAsJsonObject("SKIN");
            if (skin == null || !skin.has("url")) {
                return null;
            }
            String url = skin.get("url").getAsString();
            SkinVariant variant = SkinVariant.CLASSIC;
            if (skin.has("metadata") && skin.get("metadata").isJsonObject()) {
                JsonObject metadata = skin.getAsJsonObject("metadata");
                if (metadata.has("model")) {
                    variant = SkinVariant.parseOrDefault(metadata.get("model").getAsString(), SkinVariant.CLASSIC);
                }
            }
            String cape = null;
            JsonObject capeObject = textures.getAsJsonObject("CAPE");
            if (capeObject != null && capeObject.has("url")) {
                cape = capeObject.get("url").getAsString();
            }
            return new TextureInfo(url, variant, cape);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static byte[] decode(String value) {
        String normalized = value.trim();
        int remainder = normalized.length() % 4;
        if (remainder != 0) {
            normalized = normalized + "=".repeat(4 - remainder);
        }
        return Base64.getDecoder().decode(normalized);
    }
}
