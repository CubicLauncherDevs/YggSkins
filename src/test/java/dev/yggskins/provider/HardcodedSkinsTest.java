package dev.yggskins.provider;

import dev.yggskins.skin.SkinData;
import dev.yggskins.skin.SkinSource;
import dev.yggskins.skin.SkinVariant;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HardcodedSkinsTest {

    @Test
    void parsesSkinEntries() {
        Map<String, SkinData> skins = HardcodedSkins.parse("""
                {
                  "skins": {
                    "hero": {"value": "abc", "signature": "sig", "variant": "slim"}
                  }
                }
                """);
        assertEquals(1, skins.size());
        SkinData data = skins.get("hero");
        assertEquals("abc", data.value());
        assertEquals("sig", data.signature());
        assertEquals(SkinVariant.SLIM, data.variant());
        assertEquals(SkinSource.HARDCODED, data.source());
    }

    @Test
    void ignoresBrokenInput() {
        assertTrue(HardcodedSkins.parse("nonsense").isEmpty());
        assertTrue(HardcodedSkins.parse("").isEmpty());
        assertTrue(HardcodedSkins.parse("{\"skins\":{\"x\":{}}}").isEmpty());
    }
}
