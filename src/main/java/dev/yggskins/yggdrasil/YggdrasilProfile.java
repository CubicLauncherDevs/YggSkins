package dev.yggskins.yggdrasil;

import dev.yggskins.skin.SkinProperty;

import java.util.List;
import java.util.UUID;

/**
 * Perfil devuelto por el sessionserver.
 */
public record YggdrasilProfile(UUID id, String name, List<SkinProperty> properties) {

    public SkinProperty textures() {
        if (properties == null) {
            return null;
        }
        for (SkinProperty property : properties) {
            if (SkinProperty.TEXTURES.equals(property.name())) {
                return property;
            }
        }
        return null;
    }
}
