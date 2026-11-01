package dev.yggskins.provider;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpServer;
import dev.yggskins.skin.SkinData;
import dev.yggskins.skin.SkinVariant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MineSkinProviderHttpTest {

    private HttpServer server;
    private String endpoint;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v2/generate", exchange -> {
            String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String body = request.contains("unsigned")
                    ? unsignedResponse()
                    : signedResponse();
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/v2/generate";
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private MineSkinProvider provider(boolean allowUnsigned) {
        return new MineSkinProvider(true, "", "public", allowUnsigned, List.of(), 5, endpoint);
    }

    @Test
    void parsesSignedResponse() throws Exception {
        SkinData data = provider(true).fetch("https://8.8.8.8/skin.png", SkinVariant.SLIM);
        assertEquals("VALUE", data.value());
        assertEquals("SIGNATURE", data.signature());
        assertTrue(data.signed());
        assertEquals(SkinVariant.SLIM, data.variant());
        assertEquals("https://8.8.8.8/skin.png", data.sourceKey());
    }

    @Test
    void keepsUnsignedResponseWhenAllowed() throws Exception {
        SkinData data = provider(true).fetch("https://8.8.8.8/unsigned.png", SkinVariant.CLASSIC);
        assertEquals("VALUE", data.value());
        assertNull(data.signature());
        assertFalse(data.signed());
    }

    @Test
    void rejectsUnsignedResponseWhenNotAllowed() {
        SkinProviderException error = assertThrows(SkinProviderException.class,
                () -> provider(false).fetch("https://8.8.8.8/unsigned.png", SkinVariant.CLASSIC));
        assertEquals("skin.url.failed", error.messageKey());
    }

    private static String signedResponse() {
        return response("SIGNATURE");
    }

    private static String unsignedResponse() {
        return response(null);
    }

    private static String response(String signature) {
        JsonObject data = new JsonObject();
        data.addProperty("value", "VALUE");
        if (signature != null) {
            data.addProperty("signature", signature);
        }
        JsonObject texture = new JsonObject();
        texture.add("data", data);
        JsonObject skin = new JsonObject();
        skin.addProperty("variant", "CLASSIC");
        skin.add("texture", texture);
        JsonObject root = new JsonObject();
        root.addProperty("success", true);
        root.add("skin", skin);
        return root.toString();
    }
}
