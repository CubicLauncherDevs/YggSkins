package dev.yggskins.skin;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TextureInfoTest {

    static String encode(String json) {
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void parsesSlimTexture() {
        String value = encode("""
                {"textures":{"SKIN":{"url":"http://textures.minecraft.net/texture/abc","metadata":{"model":"slim"}}}}
                """);
        TextureInfo info = TextureInfo.parse(value);
        assertEquals("http://textures.minecraft.net/texture/abc", info.url());
        assertEquals(SkinVariant.SLIM, info.variant());
    }

    @Test
    void defaultsToClassicWithoutMetadata() {
        String value = encode("""
                {"textures":{"SKIN":{"url":"http://textures.minecraft.net/texture/abc"}}}
                """);
        assertEquals(SkinVariant.CLASSIC, TextureInfo.parse(value).variant());
    }

    @Test
    void returnsNullOnInvalidInput() {
        assertNull(TextureInfo.parse(null));
        assertNull(TextureInfo.parse("no-base64!"));
        assertNull(TextureInfo.parse(encode("{\"textures\":{}}")));
        assertNull(TextureInfo.parse(encode("{}")));
    }
}
