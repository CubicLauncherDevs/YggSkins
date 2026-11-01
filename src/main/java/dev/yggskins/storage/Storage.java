package dev.yggskins.storage;

import dev.yggskins.skin.SkinData;
import dev.yggskins.skin.SkinProperty;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Contrato de persistencia. Todas las operaciones son bloqueantes y deben
 * invocarse desde hilos de trabajo.
 */
public interface Storage extends AutoCloseable {

    void init() throws Exception;

    @Override
    void close();

    long saveSkin(SkinData data);

    Optional<SkinData> skinById(long id);

    void setPlayerSkin(UUID uuid, long skinId);

    void clearPlayerSkin(UUID uuid);

    Optional<Long> playerSkinId(UUID uuid);

    void touchPlayer(UUID uuid);

    void saveOriginalIfAbsent(UUID uuid, SkinProperty property);

    Optional<SkinProperty> originalSkin(UUID uuid);

    Optional<SkinData> customSkin(String name);

    boolean customSkinExists(String name);

    void setCustomSkin(String name, long skinId, String displayName);

    boolean setCustomDisplayName(String name, String displayName);

    List<String> customSkinNames();

    int purgeOlderThan(long epochSeconds);

    long countSkins();

    long countPlayers();

    String type();
}
