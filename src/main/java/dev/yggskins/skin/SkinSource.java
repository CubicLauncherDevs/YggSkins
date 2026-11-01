package dev.yggskins.skin;

import java.util.Locale;

/**
 * Origen de una skin almacenada.
 */
public enum SkinSource {
    YGGDRASIL,
    MOJANG,
    MINESKIN,
    CUSTOM,
    HARDCODED;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static SkinSource parse(String input) {
        if (input == null || input.isBlank()) {
            return CUSTOM;
        }
        try {
            return valueOf(input.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return CUSTOM;
        }
    }
}
