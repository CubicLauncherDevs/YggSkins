package dev.yggskins.provider;

import dev.yggskins.skin.SkinData;
import dev.yggskins.skin.SkinVariant;
import dev.yggskins.storage.Storage;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Resuelve skins personalizadas guardadas en base de datos o en hardcoded_skins.json.
 * Tambien admite el prefijo "player:" para delegar en Yggdrasil.
 */
public final class CustomProvider implements SkinProvider {

    public static final String PLAYER_PREFIX = "player:";

    private final Storage storage;
    private final YggdrasilProvider yggdrasil;
    private final Map<String, SkinData> hardcoded;

    public CustomProvider(Storage storage, YggdrasilProvider yggdrasil, Map<String, SkinData> hardcoded) {
        this.storage = storage;
        this.yggdrasil = yggdrasil;
        this.hardcoded = hardcoded;
    }

    @Override
    public String name() {
        return "custom";
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public SkinData fetch(String key, SkinVariant variant) throws SkinProviderException {
        if (key == null || key.isBlank()) {
            return null;
        }
        if (key.toLowerCase(Locale.ROOT).startsWith(PLAYER_PREFIX)) {
            return yggdrasil.fetch(key.substring(PLAYER_PREFIX.length()), variant);
        }
        String name = key.toLowerCase(Locale.ROOT);
        Optional<SkinData> stored = storage.customSkin(name);
        if (stored.isPresent()) {
            SkinData data = stored.get();
            return variant == null ? data : new SkinData(data.id(), data.value(), data.signature(), variant,
                    data.source(), data.sourceKey(), data.createdAt());
        }
        SkinData local = hardcoded.get(name);
        if (local != null) {
            return variant == null ? local : new SkinData(local.id(), local.value(), local.signature(), variant,
                    local.source(), local.sourceKey(), local.createdAt());
        }
        return null;
    }

    public SkinData hardcoded(String name) {
        return name == null ? null : hardcoded.get(name.toLowerCase(Locale.ROOT));
    }
}
