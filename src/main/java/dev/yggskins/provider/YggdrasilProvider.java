package dev.yggskins.provider;

import dev.yggskins.skin.SkinData;
import dev.yggskins.skin.SkinProperty;
import dev.yggskins.skin.SkinSource;
import dev.yggskins.skin.SkinVariant;
import dev.yggskins.skin.TextureInfo;
import dev.yggskins.util.UuidUtil;
import dev.yggskins.yggdrasil.YggdrasilClient;
import dev.yggskins.yggdrasil.YggdrasilException;
import dev.yggskins.yggdrasil.YggdrasilProfile;
import dev.yggskins.yggdrasil.YggdrasilSettings;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Obtiene skins del servidor Yggdrasil configurado (authlib-injector).
 */
public final class YggdrasilProvider implements SkinProvider {

    private static final Pattern NAME = Pattern.compile("^[A-Za-z0-9_]{1,16}$");

    private final YggdrasilClient client;
    private final YggdrasilSettings settings;

    public YggdrasilProvider(YggdrasilClient client) {
        this.client = client;
        this.settings = client.settings();
    }

    @Override
    public String name() {
        return "yggdrasil";
    }

    @Override
    public boolean available() {
        return client.available();
    }

    @Override
    public SkinData fetch(String key, SkinVariant variant) throws SkinProviderException {
        if (key == null || key.isBlank()) {
            throw new SkinProviderException("skin.invalid-name", key);
        }
        try {
            UUID id = UuidUtil.parse(key);
            YggdrasilProfile profile;
            if (id != null) {
                profile = client.profile(id);
            } else {
                if (!NAME.matcher(key).matches()) {
                    throw new SkinProviderException("skin.invalid-name", key);
                }
                id = client.idByName(key);
                if (id == null) {
                    return null;
                }
                profile = client.profile(id);
            }
            if (profile == null) {
                return null;
            }
            SkinProperty textures = profile.textures();
            if (textures == null) {
                return null;
            }
            if (settings.requireSignature() && !textures.signed()) {
                throw new SkinProviderException("skin.unsigned-rejected", key);
            }
            if (settings.verifySignature() && textures.signed() && !client.verify(textures)) {
                throw new SkinProviderException("skin.signature-invalid", key);
            }
            SkinVariant resolved = variant;
            if (resolved == null) {
                TextureInfo info = textures.texture();
                resolved = info == null ? SkinVariant.CLASSIC : info.variant();
            }
            return new SkinData(0L, textures.value(), textures.signature(), resolved,
                    SkinSource.YGGDRASIL, id.toString().toLowerCase(Locale.ROOT), System.currentTimeMillis() / 1000L);
        } catch (YggdrasilException e) {
            throw new SkinProviderException("general.error", e.reason(), e);
        }
    }
}
