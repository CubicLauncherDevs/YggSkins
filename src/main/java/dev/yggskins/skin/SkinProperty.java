package dev.yggskins.skin;

/**
 * Propiedad del perfil de un jugador (normalmente "textures").
 */
public record SkinProperty(String name, String value, String signature) {

    public static final String TEXTURES = "textures";

    public static SkinProperty textures(String value, String signature) {
        return new SkinProperty(TEXTURES, value, signature);
    }

    public boolean signed() {
        return signature != null && !signature.isBlank();
    }

    public TextureInfo texture() {
        return TextureInfo.parse(value);
    }
}
