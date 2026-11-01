package dev.yggskins.provider;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yggskins.skin.SkinData;
import dev.yggskins.skin.SkinSource;
import dev.yggskins.skin.SkinVariant;
import dev.yggskins.util.Http;
import dev.yggskins.util.UrlValidator;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Genera propiedades firmadas por Mojang a partir de una URL o de una imagen usando MineSkin.
 */
public final class MineSkinProvider implements SkinProvider {

    private static final String DEFAULT_ENDPOINT = "https://api.mineskin.org/v2/generate";
    private static final int MAX_IMAGE_BYTES = 1_000_000;

    private final boolean enabled;
    private final String apiKey;
    private final String visibility;
    private final boolean allowUnsignedFallback;
    private final List<String> allowedDomains;
    private final Http http;
    private final String endpoint;

    public MineSkinProvider(boolean enabled, String apiKey, String visibility,
                            boolean allowUnsignedFallback, List<String> allowedDomains, int timeoutSeconds) {
        this(enabled, apiKey, visibility, allowUnsignedFallback, allowedDomains, timeoutSeconds, DEFAULT_ENDPOINT);
    }

    public MineSkinProvider(boolean enabled, String apiKey, String visibility,
                            boolean allowUnsignedFallback, List<String> allowedDomains, int timeoutSeconds,
                            String endpoint) {
        this.endpoint = endpoint;
        this.enabled = enabled;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.visibility = visibility == null || visibility.isBlank() ? "public" : visibility;
        this.allowUnsignedFallback = allowUnsignedFallback;
        this.allowedDomains = allowedDomains == null ? List.of() : List.copyOf(allowedDomains);
        this.http = new Http(timeoutSeconds);
    }

    @Override
    public String name() {
        return "mineskin";
    }

    @Override
    public boolean available() {
        return enabled;
    }

    /**
     * Genera una skin a partir de una URL indicada por el usuario. MineSkin descarga la imagen.
     */
    @Override
    public SkinData fetch(String key, SkinVariant variant) throws SkinProviderException {
        if (!enabled) {
            throw new SkinProviderException("skin.provider-unavailable", name());
        }
        UrlValidator.Result validation = UrlValidator.validate(key, allowedDomains);
        if (!validation.valid()) {
            throw new SkinProviderException(
                    validation.blocked() ? "skin.url.blocked" : "skin.url.invalid", key);
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("variant", variant == null ? SkinVariant.CLASSIC.model() : variant.model());
        payload.addProperty("visibility", visibility);
        payload.addProperty("url", key);

        try {
            Http.Response response = http.postJson(endpoint, payload.toString(), headers());
            if (!response.ok()) {
                throw new SkinProviderException("skin.url.failed", "HTTP " + response.status());
            }
            return parse(response.body(), key, variant);
        } catch (IOException e) {
            throw new SkinProviderException("skin.url.failed", "io", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SkinProviderException("skin.url.failed", "interrupted", e);
        }
    }

    /**
     * Re-firma una textura descargandola y subiendola a MineSkin. Se usa para skins de
     * servidores Yggdrasil que bloquean las descargas de terceros.
     */
    public SkinData resign(String textureUrl, SkinVariant variant) throws SkinProviderException {
        if (!enabled) {
            throw new SkinProviderException("skin.provider-unavailable", name());
        }
        UrlValidator.Result validation = UrlValidator.validate(textureUrl, List.of());
        if (!validation.valid()) {
            throw new SkinProviderException(
                    validation.blocked() ? "skin.url.blocked" : "skin.url.invalid", textureUrl);
        }

        byte[] image;
        try {
            Http.Bytes response = http.getBytes(textureUrl, Map.of());
            if (!response.ok() || response.body() == null || response.body().length == 0) {
                throw new SkinProviderException("skin.url.failed", "textura inaccesible");
            }
            if (response.body().length > MAX_IMAGE_BYTES) {
                throw new SkinProviderException("skin.url.failed", "textura demasiado grande");
            }
            image = response.body();
        } catch (IOException e) {
            throw new SkinProviderException("skin.url.failed", "io", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SkinProviderException("skin.url.failed", "interrupted", e);
        }

        String boundary = "----YggSkins" + Long.toHexString(System.nanoTime());
        byte[] body = multipart(boundary, variant == null ? SkinVariant.CLASSIC.model() : variant.model(), visibility, image);
        try {
            Http.Response response = http.postBytes(endpoint, body,
                    "multipart/form-data; boundary=" + boundary, headers());
            if (!response.ok()) {
                throw new SkinProviderException("skin.url.failed", "HTTP " + response.status());
            }
            return parse(response.body(), textureUrl, variant);
        } catch (IOException e) {
            throw new SkinProviderException("skin.url.failed", "io", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SkinProviderException("skin.url.failed", "interrupted", e);
        }
    }

    private Map<String, String> headers() {
        Map<String, String> headers = new HashMap<>();
        if (!apiKey.isEmpty()) {
            headers.put("Authorization", "Bearer " + apiKey);
        }
        return headers;
    }

    private SkinData parse(String body, String key, SkinVariant variant) throws SkinProviderException {
        try {
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            JsonObject skin = json.has("skin") && json.get("skin").isJsonObject()
                    ? json.getAsJsonObject("skin") : json;
            JsonObject data = objectAt(skin, "texture", "data");
            if (data == null) {
                data = objectAt(skin, "data");
            }
            String value = data == null ? null : stringAt(data, "value");
            String signature = data == null ? null : stringAt(data, "signature");
            if (value == null || value.isBlank()) {
                throw new SkinProviderException("skin.url.failed", "respuesta invalida");
            }
            if ((signature == null || signature.isBlank()) && !allowUnsignedFallback) {
                throw new SkinProviderException("skin.url.failed", "sin firma");
            }
            SkinVariant resolved = variant;
            if (resolved == null) {
                resolved = SkinVariant.parseOrDefault(stringAt(skin, "variant"), SkinVariant.CLASSIC);
            }
            return new SkinData(0L, value, signature, resolved, SkinSource.MINESKIN,
                    key.trim().toLowerCase(Locale.ROOT), System.currentTimeMillis() / 1000L);
        } catch (RuntimeException e) {
            throw new SkinProviderException("skin.url.failed", "respuesta invalida", e);
        }
    }

    static byte[] multipart(String boundary, String variant, String visibility, byte[] image)
            throws SkinProviderException {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            writeField(out, boundary, "variant", variant);
            writeField(out, boundary, "visibility", visibility);
            out.writeBytes(("--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"file\"; filename=\"skin.png\"\r\n"
                    + "Content-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            out.writeBytes(image);
            out.writeBytes(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            return out.toByteArray();
        } catch (IOException e) {
            throw new SkinProviderException("skin.url.failed", "multipart", e);
        }
    }

    private static void writeField(ByteArrayOutputStream out, String boundary, String name, String value)
            throws IOException {
        out.writeBytes(("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n"
                + value + "\r\n").getBytes(StandardCharsets.UTF_8));
    }

    private static JsonObject objectAt(JsonObject root, String... path) {
        JsonObject current = root;
        for (String step : path) {
            if (current == null || !current.has(step) || !current.get(step).isJsonObject()) {
                return null;
            }
            current = current.getAsJsonObject(step);
        }
        return current;
    }

    private static String stringAt(JsonObject object, String field) {
        if (object == null || !object.has(field) || object.get(field).isJsonNull()) {
            return null;
        }
        JsonElement element = object.get(field);
        return element.isJsonPrimitive() ? element.getAsString() : null;
    }
}
