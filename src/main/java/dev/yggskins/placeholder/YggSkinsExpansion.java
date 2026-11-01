package dev.yggskins.placeholder;

import dev.yggskins.YggSkinsPlugin;
import dev.yggskins.skin.SkinData;
import dev.yggskins.skin.TextureInfo;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * Expansión de PlaceholderAPI.
 */
public final class YggSkinsExpansion extends PlaceholderExpansion {

    private final YggSkinsPlugin plugin;

    public YggSkinsExpansion(YggSkinsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "yggskins";
    }

    @Override
    public @NotNull String getAuthor() {
        return "YggSkins";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) {
            return null;
        }
        String key = params.toLowerCase(Locale.ROOT);
        SkinData skin = plugin.skinService().currentSkin(player.getUniqueId());
        if (key.equals("has_skin")) {
            return String.valueOf(skin != null);
        }
        if (skin == null) {
            return "";
        }
        return switch (key) {
            case "skin", "name" -> skin.displayName();
            case "source" -> skin.source().id();
            case "variant", "model" -> skin.variant() == null ? "classic" : skin.variant().model();
            case "texture", "url" -> {
                TextureInfo texture = skin.texture();
                yield texture == null ? "" : texture.url();
            }
            case "signed" -> String.valueOf(skin.signed());
            default -> null;
        };
    }
}
