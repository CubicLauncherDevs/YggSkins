package dev.yggskins.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UrlValidatorTest {

    @Test
    void acceptsPublicHttpUrls() {
        UrlValidator.Result result = UrlValidator.validate("https://8.8.8.8/skin.png", List.of());
        assertTrue(result.valid(), result.reason());
    }

    @Test
    void rejectsUnsupportedSchemes() {
        assertFalse(UrlValidator.validate("ftp://8.8.8.8/skin.png", List.of()).valid());
        assertFalse(UrlValidator.validate("file:///etc/passwd", List.of()).valid());
        assertFalse(UrlValidator.validate("not a url", List.of()).valid());
        assertFalse(UrlValidator.validate("", List.of()).valid());
    }

    @Test
    void blocksLocalAndPrivateAddresses() {
        assertTrue(UrlValidator.validate("http://127.0.0.1/skin.png", List.of()).blocked());
        assertTrue(UrlValidator.validate("http://localhost/skin.png", List.of()).blocked());
        assertTrue(UrlValidator.validate("http://192.168.1.10/skin.png", List.of()).blocked());
        assertTrue(UrlValidator.validate("http://10.0.0.5/skin.png", List.of()).blocked());
        assertTrue(UrlValidator.validate("http://169.254.1.1/skin.png", List.of()).blocked());
        assertTrue(UrlValidator.validate("http://[::1]/skin.png", List.of()).blocked());
    }

    @Test
    void enforcesDomainAllowList() {
        UrlValidator.Result allowed = UrlValidator.validate("https://textures.minecraft.net/x.png", List.of("minecraft.net"));
        assertTrue(allowed.valid(), allowed.reason());

        UrlValidator.Result blocked = UrlValidator.validate("https://example.com/x.png", List.of("minecraft.net"));
        assertTrue(blocked.blocked());
    }

    @Test
    void rejectsOverlongUrls() {
        String longUrl = "https://8.8.8.8/" + "a".repeat(600);
        assertFalse(UrlValidator.validate(longUrl, List.of()).valid());
    }
}
