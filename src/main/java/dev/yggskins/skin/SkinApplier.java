package dev.yggskins.skin;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Aplica propiedades "textures" al perfil del jugador sin NMS.
 * Debe invocarse siempre desde el hilo principal.
 */
public final class SkinApplier {

    private SkinApplier() {
    }

    public static void apply(Player player, SkinProperty property) {
        if (property == null) {
            strip(player);
            return;
        }
        PlayerProfile profile = player.getPlayerProfile();
        List<ProfileProperty> properties = new ArrayList<>();
        for (ProfileProperty existing : profile.getProperties()) {
            if (!SkinProperty.TEXTURES.equalsIgnoreCase(existing.getName())) {
                properties.add(existing);
            }
        }
        properties.add(new ProfileProperty(SkinProperty.TEXTURES, property.value(), property.signature()));
        profile.setProperties(properties);
        player.setPlayerProfile(profile);
        refresh(player);
    }

    public static void strip(Player player) {
        PlayerProfile profile = player.getPlayerProfile();
        if (!profile.hasProperty(SkinProperty.TEXTURES)) {
            return;
        }
        profile.removeProperty(SkinProperty.TEXTURES);
        player.setPlayerProfile(profile);
        refresh(player);
    }

    public static SkinProperty current(Player player) {
        for (ProfileProperty property : player.getPlayerProfile().getProperties()) {
            if (SkinProperty.TEXTURES.equals(property.getName())) {
                return new SkinProperty(property.getName(), property.getValue(), property.getSignature());
            }
        }
        return null;
    }

    private static void refresh(Player player) {
        try {
            player.sendHealthUpdate();
        } catch (Throwable ignored) {
            // Metodo no disponible en algunas versiones; el perfil ya fue aplicado.
        }
    }
}
