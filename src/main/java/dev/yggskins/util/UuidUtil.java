package dev.yggskins.util;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Utilidades para trabajar con UUID en formato con o sin guiones.
 */
public final class UuidUtil {

    private static final Pattern DASHED = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final Pattern UNDASHED = Pattern.compile("^[0-9a-fA-F]{32}$");

    private UuidUtil() {
    }

    public static UUID parse(String input) {
        if (input == null) {
            return null;
        }
        String value = input.trim();
        if (DASHED.matcher(value).matches()) {
            return UUID.fromString(value.toLowerCase(Locale.ROOT));
        }
        if (UNDASHED.matcher(value).matches()) {
            String lower = value.toLowerCase(Locale.ROOT);
            return new UUID(
                    Long.parseUnsignedLong(lower.substring(0, 16), 16),
                    Long.parseUnsignedLong(lower.substring(16), 16));
        }
        return null;
    }

    public static String undashed(UUID uuid) {
        return uuid.toString().replace("-", "");
    }
}
