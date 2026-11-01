package dev.yggskins.provider;

import dev.yggskins.skin.SkinVariant;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MineSkinProviderTest {

    private final MineSkinProvider provider = new MineSkinProvider(true, "", "public", true, List.of(), 5);

    @Test
    void blocksPrivateAddressesBeforeCallingTheApi() {
        SkinProviderException error = assertThrows(SkinProviderException.class,
                () -> provider.fetch("http://127.0.0.1/skin.png", SkinVariant.CLASSIC));
        assertEquals("skin.url.blocked", error.messageKey());
    }

    @Test
    void rejectsInvalidUrls() {
        SkinProviderException error = assertThrows(SkinProviderException.class,
                () -> provider.fetch("ftp://example.org/skin.png", SkinVariant.CLASSIC));
        assertEquals("skin.url.invalid", error.messageKey());
    }

    @Test
    void unavailableWhenDisabled() {
        MineSkinProvider disabled = new MineSkinProvider(false, "", "public", true, List.of(), 5);
        SkinProviderException error = assertThrows(SkinProviderException.class,
                () -> disabled.fetch("https://8.8.8.8/skin.png", SkinVariant.CLASSIC));
        assertEquals("skin.provider-unavailable", error.messageKey());
    }
}
