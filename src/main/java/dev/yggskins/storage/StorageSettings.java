package dev.yggskins.storage;

import java.nio.file.Path;
import java.util.Locale;

/**
 * Configuracion resuelta del almacenamiento, independiente de Bukkit.
 */
public record StorageSettings(Type type, Path sqliteFile, Database mysql, Database postgres) {

    public enum Type {
        SQLITE,
        MYSQL,
        POSTGRESQL;

        public static Type parse(String input) {
            if (input == null) {
                return SQLITE;
            }
            return switch (input.trim().toLowerCase(Locale.ROOT)) {
                case "mysql", "mariadb" -> MYSQL;
                case "postgres", "postgresql", "pg" -> POSTGRESQL;
                default -> SQLITE;
            };
        }
    }

    public record Database(String host, int port, String database, String username, String password) {
    }

    public static StorageSettings sqlite(Path file) {
        return new StorageSettings(Type.SQLITE, file, null, null);
    }
}
