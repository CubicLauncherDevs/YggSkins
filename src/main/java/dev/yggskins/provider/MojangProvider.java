package dev.yggskins.provider;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yggskins.skin.SkinData;
import dev.yggskins.skin.SkinProperty;
import dev.yggskins.skin.SkinSource;
import dev.yggskins.skin.SkinVariant;
import dev.yggskins.skin.TextureInfo;
import dev.yggskins.util.Http;
import dev.yggskins.util.UuidUtil;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Proveedor opcional que consulta las APIs publicas de Mojang.
 */
public final class MojangProvider implements SkinProvider {

    private static final Pattern NAME = Pattern.compile("^[A-Za-z0-9_]{1,16}$");
    private static final String USERNAME_URL = "https://api.mojang.com/users/profiles/minecraft/";
    private static final String SESSION_URL = "https://sessionserver.mojang.com/session/minecraft/profile/";

    private final Http http;
    private final boolean enabled;

    public MojangProvider(boolean enabled, int timeoutSeconds) {
        this.enabled = enabled;
        this.http = new Http(timeoutSeconds);
    }

    @Override
    public String name() {
        return "mojang";
    }

    @Override
    public boolean available() {
        return enabled;
    }

    @Override
    public SkinData fetch(String key, SkinVariant variant) throws SkinProviderException {
        if (!enabled) {
            return null;
        }
        if (key == null || key.isBlank()) {
            throw new SkinProviderException("skin.invalid-name", key);
        }
        try {
            UUID id = UuidUtil.parse(key);
            if (id == null) {
                if (!NAME.matcher(key).matches()) {
                    throw new SkinProviderException("skin.invalid-name", key);
                }
                Http.Response lookup = http.get(USERNAME_URL + key, Map.of());
                if (lookup.status() == 204 || lookup.status() == 404) {
                    return null;
                }
                if (!lookup.ok()) {
                    throw new SkinProviderException("general.error", "lookup-" + lookup.status());
                }
                JsonObject json = JsonParser.parseString(lookup.body()).getAsJsonObject();
                if (!json.has("id")) {
                    return null;
                }
                id = UuidUtil.parse(json.get("id").getAsString());
            }
            if (id == null) {
                return null;
            }
            Http.Response session = http.get(SESSION_URL + UuidUtil.undashed(id) + "?unsigned=false", Map.of());
            if (session.status() == 204 || session.status() == 404) {
                return null;
            }
            if (!session.ok()) {
                throw new SkinProviderException("general.error", "session-" + session.status());
            }
            JsonObject profile = JsonParser.parseString(session.body()).getAsJsonObject();
            if (!profile.has("properties")) {
                return null;
            }
            for (var element : profile.getAsJsonArray("properties")) {
                JsonObject property = element.getAsJsonObject();
                if (!property.has("name") || !"textures".equals(property.get("name").getAsString())) {
                    continue;
                }
                String value = property.get("value").getAsString();
                String signature = property.has("signature") && !property.get("signature").isJsonNull()
                        ? property.get("signature").getAsString() : null;
                SkinVariant resolved = variant;
                if (resolved == null) {
                    TextureInfo info = SkinProperty.textures(value, signature).texture();
                    resolved = info == null ? SkinVariant.CLASSIC : info.variant();
                }
                return new SkinData(0L, value, signature, resolved, SkinSource.MOJANG,
                        id.toString().toLowerCase(Locale.ROOT), System.currentTimeMillis() / 1000L);
            }
            return null;
        } catch (IOException e) {
            throw new SkinProviderException("general.error", "io", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SkinProviderException("general.error", "interrupted", e);
        }
    }
}
