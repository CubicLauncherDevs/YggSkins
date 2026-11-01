package dev.yggskins.provider;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MineSkinMultipartTest {

    private static final byte[] IMAGE = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 1, 2, 3};

    @Test
    void buildsMultipartBody() throws Exception {
        byte[] body = MineSkinProvider.multipart("BOUNDARY", "slim", "public", IMAGE);
        String text = new String(body, StandardCharsets.ISO_8859_1);

        assertTrue(text.contains("--BOUNDARY\r\nContent-Disposition: form-data; name=\"variant\"\r\n\r\nslim\r\n"));
        assertTrue(text.contains("name=\"visibility\"\r\n\r\npublic\r\n"));
        assertTrue(text.contains("name=\"file\"; filename=\"skin.png\"\r\nContent-Type: image/png\r\n\r\n"));
        assertTrue(text.endsWith("\r\n--BOUNDARY--\r\n"));
        assertTrue(containsImage(body));
    }

    @Test
    void rejectsBlockedTextureUrls() {
        MineSkinProvider provider = new MineSkinProvider(true, "", "public", true, java.util.List.of(), 5);
        SkinProviderException error = assertThrows(SkinProviderException.class,
                () -> provider.resign("http://127.0.0.1/skin.png", null));
        assertEquals("skin.url.blocked", error.messageKey());
    }

    private static boolean containsImage(byte[] body) {
        for (int i = 0; i + IMAGE.length <= body.length; i++) {
            boolean match = true;
            for (int j = 0; j < IMAGE.length; j++) {
                if (body[i + j] != IMAGE[j]) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return true;
            }
        }
        return false;
    }
}
