package dev.yggskins.yggdrasil;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.yggskins.provider.SkinProviderException;
import dev.yggskins.provider.YggdrasilProvider;
import dev.yggskins.skin.SkinData;
import dev.yggskins.skin.SkinProperty;
import dev.yggskins.skin.SkinSource;
import dev.yggskins.skin.SkinVariant;
import dev.yggskins.util.UuidUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.Base64;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YggdrasilClientTest {

    private static final UUID SIGNED_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID UNSIGNED_ID = UUID.fromString("66666666-7777-8888-9999-000000000000");
    private static final UUID ERROR_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private static final Logger LOGGER = Logger.getLogger("YggdrasilClientTest");

    private static HttpServer server;
    private static String baseUrl;
    private static String texturesValue;
    private static String texturesSignature;

    private YggdrasilClient client;

    @BeforeAll
    static void startServer() throws Exception {
        KeyPair pair = SignatureVerifierTest.generate();
        String json = """
                {"textures":{"SKIN":{"url":"http://textures.minecraft.net/texture/abc","metadata":{"model":"slim"}}}}
                """.trim();
        texturesValue = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        texturesSignature = SignatureVerifierTest.sign(texturesValue, pair);
        String pem = SignatureVerifierTest.toPem(pair.getPublic());

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);

        server.createContext("/", exchange -> {
            if (!exchange.getRequestURI().getPath().equals("/")) {
                respond(exchange, 404, "");
                return;
            }
            JsonObject metadata = new JsonObject();
            metadata.addProperty("signaturePublickey", pem);
            JsonObject meta = new JsonObject();
            meta.addProperty("serverName", "TestServer");
            metadata.add("meta", meta);
            respond(exchange, 200, metadata.toString());
        });

        server.createContext("/api/profiles/minecraft", exchange -> {
            if (!"POST".equals(exchange.getRequestMethod())) {
                respond(exchange, 405, "");
                return;
            }
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (body.contains("Steve")) {
                respond(exchange, 200, "[{\"id\":\"" + UuidUtil.undashed(SIGNED_ID) + "\",\"name\":\"Steve\"}]");
            } else {
                respond(exchange, 200, "[]");
            }
        });

        server.createContext("/sessionserver/session/minecraft/profile/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.endsWith(UuidUtil.undashed(SIGNED_ID))) {
                respond(exchange, 200, profileJson(SIGNED_ID, "Steve", texturesValue, texturesSignature));
            } else if (path.endsWith(UuidUtil.undashed(UNSIGNED_ID))) {
                respond(exchange, 200, profileJson(UNSIGNED_ID, "Alex", texturesValue, null));
            } else if (path.endsWith(UuidUtil.undashed(ERROR_ID))) {
                respond(exchange, 500, "{\"error\":\"boom\"}");
            } else {
                respond(exchange, 204, "");
            }
        });

        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @BeforeEach
    void createClient() {
        client = new YggdrasilClient(new YggdrasilSettings(baseUrl, true, true, 5, 0), LOGGER);
    }

    @Test
    void readsMetadata() throws Exception {
        YggdrasilMetadata metadata = client.metadata();
        assertEquals(baseUrl, metadata.apiRoot());
        assertEquals("TestServer", metadata.serverName());
        assertNotNull(metadata.signaturePublicKey());
    }

    @Test
    void resolvesUuidByName() throws Exception {
        assertEquals(SIGNED_ID, client.idByName("Steve"));
        assertNull(client.idByName("Nobody"));
    }

    @Test
    void readsSignedProfile() throws Exception {
        YggdrasilProfile profile = client.profile(SIGNED_ID);
        assertNotNull(profile);
        assertEquals("Steve", profile.name());
        SkinProperty textures = profile.textures();
        assertNotNull(textures);
        assertTrue(textures.signed());
        assertTrue(client.verify(textures));
    }

    @Test
    void rejectsTamperedSignature() throws Exception {
        SkinProperty tampered = new SkinProperty("textures", texturesValue + "x", texturesSignature);
        assertFalse(client.verify(tampered));
    }

    @Test
    void returnsNullForUnknownProfile() throws Exception {
        assertNull(client.profile(UUID.randomUUID()));
    }

    @Test
    void failsOnServerError() {
        assertThrows(YggdrasilException.class, () -> client.profile(ERROR_ID));
    }

    @Test
    void providerBuildsSkinData() throws Exception {
        YggdrasilProvider provider = new YggdrasilProvider(client);
        SkinData data = provider.fetch("Steve", null);
        assertNotNull(data);
        assertEquals(SkinSource.YGGDRASIL, data.source());
        assertEquals(SkinVariant.SLIM, data.variant());
        assertEquals(SIGNED_ID.toString(), data.sourceKey());
        assertTrue(data.signed());
    }

    @Test
    void providerRejectsUnsignedSkin() {
        YggdrasilProvider provider = new YggdrasilProvider(client);
        SkinProviderException error = assertThrows(SkinProviderException.class,
                () -> provider.fetch(UuidUtil.undashed(UNSIGNED_ID), null));
        assertEquals("skin.unsigned-rejected", error.messageKey());
    }

    @Test
    void providerAllowsUnsignedWhenDisabled() throws Exception {
        YggdrasilClient permissive = new YggdrasilClient(new YggdrasilSettings(baseUrl, false, false, 5, 0), LOGGER);
        SkinData data = new YggdrasilProvider(permissive).fetch(UuidUtil.undashed(UNSIGNED_ID), null);
        assertNotNull(data);
        assertFalse(data.signed());
    }

    private static String profileJson(UUID id, String name, String value, String signature) {
        JsonObject root = new JsonObject();
        root.addProperty("id", UuidUtil.undashed(id));
        root.addProperty("name", name);
        JsonObject property = new JsonObject();
        property.addProperty("name", "textures");
        property.addProperty("value", value);
        if (signature != null) {
            property.addProperty("signature", signature);
        }
        JsonArray properties = new JsonArray();
        properties.add(property);
        root.add("properties", properties);
        return root.toString();
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        if (bytes.length == 0) {
            exchange.sendResponseHeaders(status, -1);
        } else {
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
        }
        exchange.close();
    }
}
