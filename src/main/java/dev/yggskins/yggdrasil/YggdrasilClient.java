package dev.yggskins.yggdrasil;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yggskins.skin.SkinCache;
import dev.yggskins.skin.SkinProperty;
import dev.yggskins.util.Http;
import dev.yggskins.util.UuidUtil;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Cliente del protocolo Yggdrasil / authlib-injector.
 */
public final class YggdrasilClient {

    private static final String PREFETCHED_PROPERTY = "authlibinjector.yggdrasil.prefetched";

    private final YggdrasilSettings settings;
    private final Logger logger;
    private final Http http;
    private final SkinCache<String, YggdrasilProfile> profileCache;
    private final SkinCache<String, UUID> uuidCache;

    private volatile YggdrasilMetadata metadata;
    private volatile PublicKey publicKey;

    public YggdrasilClient(YggdrasilSettings settings, Logger logger) {
        this.settings = settings;
        this.logger = logger;
        this.http = new Http(settings.timeoutSeconds());
        this.profileCache = new SkinCache<>(settings.cacheSeconds());
        this.uuidCache = new SkinCache<>(settings.cacheSeconds());
    }

    public YggdrasilSettings settings() {
        return settings;
    }

    /**
     * API Root configurada o autodetectada, o null si no hay ninguna.
     */
    public String apiRoot() {
        String configured = settings.apiRoot();
        if (configured != null && !configured.isBlank()) {
            return normalize(configured);
        }
        return detectFromJvmArguments();
    }

    public boolean available() {
        return apiRoot() != null;
    }

    public static String detectFromJvmArguments() {
        try {
            for (String argument : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
                String lower = argument.toLowerCase(Locale.ROOT);
                if (!lower.startsWith("-javaagent:") || !lower.contains("authlib-injector")) {
                    continue;
                }
                int jarEnd = lower.indexOf(".jar");
                int separator = jarEnd >= 0 ? argument.indexOf('=', jarEnd) : argument.lastIndexOf('=');
                if (separator > 0 && separator + 1 < argument.length()) {
                    return normalize(argument.substring(separator + 1));
                }
            }
        } catch (RuntimeException e) {
            // Algunos entornos no exponen los argumentos de la JVM.
        }
        return null;
    }

    private static String normalize(String root) {
        String value = root.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    public YggdrasilMetadata metadata() throws YggdrasilException {
        YggdrasilMetadata current = metadata;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (metadata != null) {
                return metadata;
            }
            String root = apiRoot();
            if (root == null) {
                throw new YggdrasilException("no-api-root");
            }
            try {
                Http.Response response = http.get(root + "/", Map.of());
                if (response.ok() && response.body() != null && !response.body().isBlank()) {
                    JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                    String key = json.has("signaturePublickey") ? json.get("signaturePublickey").getAsString() : null;
                    String serverName = null;
                    if (json.has("meta") && json.get("meta").isJsonObject()) {
                        JsonObject meta = json.getAsJsonObject("meta");
                        if (meta.has("serverName")) {
                            serverName = meta.get("serverName").getAsString();
                        }
                    }
                    metadata = new YggdrasilMetadata(root, key, serverName);
                    return metadata;
                }
                logger.warning("El API Root de Yggdrasil respondio con estado " + response.status() + ".");
            } catch (IOException | InterruptedException | RuntimeException e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                logger.log(Level.WARNING, "No se pudieron leer los metadatos de Yggdrasil en " + root, e);
            }

            YggdrasilMetadata prefetched = prefetchedMetadata(root);
            if (prefetched != null) {
                logger.info("Usando metadatos precargados por authlib-injector.");
                metadata = prefetched;
                return metadata;
            }
            throw new YggdrasilException("metadata-unavailable");
        }
    }

    private YggdrasilMetadata prefetchedMetadata(String root) {
        String value = System.getProperty(PREFETCHED_PROPERTY);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            String json = new String(Base64.getDecoder().decode(value.trim()), StandardCharsets.UTF_8);
            JsonObject object = JsonParser.parseString(json).getAsJsonObject();
            if (!object.has("signaturePublickey")) {
                return null;
            }
            String serverName = null;
            if (object.has("meta") && object.get("meta").isJsonObject()) {
                JsonObject meta = object.getAsJsonObject("meta");
                if (meta.has("serverName")) {
                    serverName = meta.get("serverName").getAsString();
                }
            }
            return new YggdrasilMetadata(root, object.get("signaturePublickey").getAsString(), serverName);
        } catch (RuntimeException e) {
            return null;
        }
    }

    public PublicKey publicKey() {
        PublicKey current = publicKey;
        if (current != null) {
            return current;
        }
        try {
            PublicKey parsed = SignatureVerifier.parsePublicKey(metadata().signaturePublicKey());
            if (parsed != null) {
                publicKey = parsed;
            }
            return parsed;
        } catch (YggdrasilException e) {
            return null;
        }
    }

    public boolean verify(SkinProperty property) {
        if (property == null || !property.signed()) {
            return false;
        }
        return SignatureVerifier.verify(property, publicKey());
    }

    public UUID idByName(String name) throws YggdrasilException {
        if (name == null || name.isBlank()) {
            return null;
        }
        UUID cached = uuidCache.get(name.toLowerCase(Locale.ROOT));
        if (cached != null) {
            return cached;
        }
        String root = metadata().apiRoot();
        String body = new Gson().toJson(new String[]{name});
        try {
            Http.Response response = http.postJson(root + "/api/profiles/minecraft", body, Map.of());
            if (response.status() == 404 || response.status() == 405) {
                throw new YggdrasilException("lookup-unsupported");
            }
            if (!response.ok()) {
                throw new YggdrasilException("lookup-status-" + response.status());
            }
            JsonElement parsed = JsonParser.parseString(response.body());
            if (!parsed.isJsonArray()) {
                throw new YggdrasilException("lookup-malformed");
            }
            JsonArray array = parsed.getAsJsonArray();
            if (array.isEmpty()) {
                return null;
            }
            JsonObject first = array.get(0).getAsJsonObject();
            if (!first.has("id")) {
                return null;
            }
            UUID id = UuidUtil.parse(first.get("id").getAsString());
            if (id != null) {
                uuidCache.put(name.toLowerCase(Locale.ROOT), id);
            }
            return id;
        } catch (IOException e) {
            throw new YggdrasilException("lookup-io", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new YggdrasilException("lookup-interrupted", e);
        }
    }

    public YggdrasilProfile profile(UUID id) throws YggdrasilException {
        if (id == null) {
            return null;
        }
        YggdrasilProfile cached = profileCache.get(id.toString());
        if (cached != null) {
            return cached;
        }
        String root = metadata().apiRoot();
        String url = root + "/sessionserver/session/minecraft/profile/" + UuidUtil.undashed(id) + "?unsigned=false";
        try {
            Http.Response response = http.get(url, Map.of());
            if (response.status() == 204 || response.status() == 404) {
                return null;
            }
            if (!response.ok()) {
                throw new YggdrasilException("profile-status-" + response.status());
            }
            if (response.body() == null || response.body().isBlank()) {
                return null;
            }
            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            List<SkinProperty> properties = new ArrayList<>();
            if (json.has("properties") && json.get("properties").isJsonArray()) {
                for (JsonElement element : json.getAsJsonArray("properties")) {
                    if (!element.isJsonObject()) {
                        continue;
                    }
                    JsonObject property = element.getAsJsonObject();
                    String name = property.has("name") ? property.get("name").getAsString() : null;
                    String value = property.has("value") ? property.get("value").getAsString() : null;
                    String signature = property.has("signature") && !property.get("signature").isJsonNull()
                            ? property.get("signature").getAsString() : null;
                    if (name != null && value != null) {
                        properties.add(new SkinProperty(name, value, signature));
                    }
                }
            }
            String name = json.has("name") ? json.get("name").getAsString() : null;
            YggdrasilProfile profile = new YggdrasilProfile(id, name, List.copyOf(properties));
            profileCache.put(id.toString(), profile);
            return profile;
        } catch (IOException e) {
            throw new YggdrasilException("profile-io", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new YggdrasilException("profile-interrupted", e);
        }
    }

    public void clearCache() {
        profileCache.clear();
        uuidCache.clear();
        metadata = null;
        publicKey = null;
    }
}
