package dev.yggskins.skin;

import java.util.Locale;

/**
 * Variante del modelo de la skin.
 */
public enum SkinVariant {
    CLASSIC,
    SLIM;

    public String model() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static SkinVariant parse(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        return switch (input.trim().toLowerCase(Locale.ROOT)) {
            case "classic", "normal", "steve", "default" -> CLASSIC;
            case "slim", "alex" -> SLIM;
            default -> null;
        };
    }

    public static SkinVariant parseOrDefault(String input, SkinVariant fallback) {
        SkinVariant parsed = parse(input);
        return parsed != null ? parsed : fallback;
    }
}
