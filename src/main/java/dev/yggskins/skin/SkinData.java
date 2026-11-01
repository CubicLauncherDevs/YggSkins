package dev.yggskins.skin;

/**
 * Skin almacenada, con su origen y clave de deduplicacion.
 */
public record SkinData(
        long id,
        String value,
        String signature,
        SkinVariant variant,
        SkinSource source,
        String sourceKey,
        long createdAt
) {

    public SkinProperty property() {
        return SkinProperty.textures(value, signature);
    }

    public boolean signed() {
        return signature != null && !signature.isBlank();
    }

    public TextureInfo texture() {
        return TextureInfo.parse(value);
    }

    public String displayName() {
        return sourceKey == null ? "" : sourceKey;
    }

    public SkinData withId(long newId) {
        return new SkinData(newId, value, signature, variant, source, sourceKey, createdAt);
    }
}
