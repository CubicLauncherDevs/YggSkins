package dev.yggskins.storage;

import dev.yggskins.skin.SkinData;
import dev.yggskins.skin.SkinProperty;
import dev.yggskins.skin.SkinSource;
import dev.yggskins.skin.SkinVariant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlStorageTest {

    private Path folder;
    private SqlStorage storage;

    @BeforeEach
    void setUp() throws Exception {
        folder = Files.createTempDirectory("yggskins-test");
        storage = new SqlStorage(StorageSettings.sqlite(folder.resolve("test.db")), Logger.getLogger("SqlStorageTest"));
        storage.init();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (storage != null) {
            storage.close();
        }
        if (folder != null) {
            try (Stream<Path> paths = Files.walk(folder)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
            }
        }
    }

    private static SkinData skin(String value, String signature, SkinVariant variant, SkinSource source, String key) {
        return new SkinData(0L, value, signature, variant, source, key, System.currentTimeMillis() / 1000L);
    }

    @Test
    void deduplicatesSkinsAndUpdatesThem() {
        long first = storage.saveSkin(skin("v1", "s1", SkinVariant.CLASSIC, SkinSource.YGGDRASIL, "uuid-1"));
        long second = storage.saveSkin(skin("v2", "s2", SkinVariant.CLASSIC, SkinSource.YGGDRASIL, "uuid-1"));
        assertEquals(first, second);
        assertEquals("v2", storage.skinById(first).orElseThrow().value());
        assertEquals(1L, storage.countSkins());
    }

    @Test
    void separatesSkinsByVariant() {
        long classic = storage.saveSkin(skin("v1", "s1", SkinVariant.CLASSIC, SkinSource.YGGDRASIL, "uuid-1"));
        long slim = storage.saveSkin(skin("v1", "s1", SkinVariant.SLIM, SkinSource.YGGDRASIL, "uuid-1"));
        assertNotEquals(classic, slim);
        assertEquals(2L, storage.countSkins());
    }

    @Test
    void storesPlayerSelection() {
        UUID uuid = UUID.randomUUID();
        long id = storage.saveSkin(skin("v1", "s1", SkinVariant.CLASSIC, SkinSource.CUSTOM, "hero"));
        storage.setPlayerSkin(uuid, id);
        assertEquals(id, storage.playerSkinId(uuid).orElseThrow());
        storage.clearPlayerSkin(uuid);
        assertTrue(storage.playerSkinId(uuid).isEmpty());
    }

    @Test
    void keepsTheFirstOriginalSkin() {
        UUID uuid = UUID.randomUUID();
        storage.saveOriginalIfAbsent(uuid, SkinProperty.textures("original", "sig1"));
        storage.saveOriginalIfAbsent(uuid, SkinProperty.textures("otra", "sig2"));
        assertEquals("original", storage.originalSkin(uuid).orElseThrow().value());
        assertEquals("sig1", storage.originalSkin(uuid).orElseThrow().signature());
    }

    @Test
    void managesCustomSkins() {
        long id = storage.saveSkin(skin("v1", "s1", SkinVariant.SLIM, SkinSource.MINESKIN, "http://x/y.png"));
        storage.setCustomSkin("hero", id, "hero");
        assertTrue(storage.customSkinExists("hero"));
        assertEquals(id, storage.customSkin("hero").orElseThrow().id());
        assertEquals(1, storage.customSkinNames().size());
        assertTrue(storage.setCustomDisplayName("hero", "El Heroe"));
        assertFalse(storage.setCustomDisplayName("missing", "nada"));
    }

    @Test
    void purgesOldDataButKeepsSelections() {
        UUID active = UUID.randomUUID();
        UUID idle = UUID.randomUUID();
        long id = storage.saveSkin(skin("v1", "s1", SkinVariant.CLASSIC, SkinSource.CUSTOM, "hero"));
        storage.setPlayerSkin(active, id);
        storage.touchPlayer(idle);
        long future = System.currentTimeMillis() / 1000L + 60L;
        int removed = storage.purgeOlderThan(future);
        assertTrue(removed >= 1);
        assertEquals(1L, storage.countSkins());
        assertEquals(1L, storage.countPlayers());
        assertEquals(id, storage.playerSkinId(active).orElseThrow());
    }
}
