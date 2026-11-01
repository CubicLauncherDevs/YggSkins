package dev.yggskins.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.yggskins.skin.SkinData;
import dev.yggskins.skin.SkinProperty;
import dev.yggskins.skin.SkinSource;
import dev.yggskins.skin.SkinVariant;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementacion JDBC sobre HikariCP compatible con SQLite, MySQL/MariaDB y PostgreSQL.
 */
public final class SqlStorage implements Storage {

    private static final int SCHEMA_VERSION = 1;

    private final StorageSettings settings;
    private final Logger logger;
    private HikariDataSource dataSource;

    public SqlStorage(StorageSettings settings, Logger logger) {
        this.settings = settings;
        this.logger = logger;
    }

    @Override
    public void init() {
        HikariConfig config = new HikariConfig();
        config.setPoolName("YggSkins");
        config.setConnectionTimeout(10_000L);
        config.setInitializationFailTimeout(10_000L);

        switch (settings.type()) {
            case SQLITE -> {
                config.setJdbcUrl("jdbc:sqlite:" + settings.sqliteFile().toAbsolutePath());
                config.setDriverClassName(driver("org.sqlite.JDBC", "dev.yggskins.libs.sqlite.JDBC"));
                config.setMaximumPoolSize(1);
            }
            case MYSQL -> {
                StorageSettings.Database db = settings.mysql();
                config.setJdbcUrl("jdbc:mariadb://" + db.host() + ":" + db.port() + "/" + db.database() + "?useUnicode=true&characterEncoding=utf8");
                config.setDriverClassName(driver("org.mariadb.jdbc.Driver", "dev.yggskins.libs.mariadb.Driver"));
                config.setUsername(db.username());
                config.setPassword(db.password());
                config.setMaximumPoolSize(5);
            }
            case POSTGRESQL -> {
                StorageSettings.Database db = settings.postgres();
                config.setJdbcUrl("jdbc:postgresql://" + db.host() + ":" + db.port() + "/" + db.database());
                config.setDriverClassName(driver("org.postgresql.Driver", "dev.yggskins.libs.postgresql.Driver"));
                config.setUsername(db.username());
                config.setPassword(db.password());
                config.setMaximumPoolSize(5);
            }
        }

        try {
            this.dataSource = new HikariDataSource(config);
            migrate();
        } catch (RuntimeException e) {
            throw new StorageException("No se pudo conectar con el almacenamiento " + type(), e);
        }
    }

    private static String driver(String original, String relocated) {
        try {
            Class.forName(relocated, false, SqlStorage.class.getClassLoader());
            return relocated;
        } catch (ClassNotFoundException e) {
            return original;
        }
    }

    private void migrate() {
        try (Connection connection = dataSource.getConnection()) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE IF NOT EXISTS schema_version (version INTEGER NOT NULL)");
            }
            int current = 0;
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("SELECT version FROM schema_version")) {
                if (result.next()) {
                    current = result.getInt(1);
                }
            }
            if (current < SCHEMA_VERSION) {
                createSchema(connection);
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("DELETE FROM schema_version");
                    statement.executeUpdate("INSERT INTO schema_version (version) VALUES (" + SCHEMA_VERSION + ")");
                }
                logger.info("Esquema de base de datos creado en su version " + SCHEMA_VERSION + ".");
            }
        } catch (SQLException e) {
            throw new StorageException("No se pudo migrar el esquema", e);
        }
    }

    private void createSchema(Connection connection) throws SQLException {
        String[] statements = switch (settings.type()) {
            case SQLITE -> new String[]{
                    """
                    CREATE TABLE IF NOT EXISTS skins (
                      id INTEGER PRIMARY KEY AUTOINCREMENT,
                      texture_value TEXT NOT NULL,
                      texture_signature TEXT,
                      variant VARCHAR(16) NOT NULL,
                      source VARCHAR(32) NOT NULL,
                      source_key VARCHAR(255) NOT NULL,
                      created_at BIGINT NOT NULL,
                      UNIQUE (source, source_key, variant)
                    )""",
                    """
                    CREATE TABLE IF NOT EXISTS players (
                      uuid VARCHAR(36) PRIMARY KEY,
                      skin_id BIGINT,
                      original_value TEXT,
                      original_signature TEXT,
                      updated_at BIGINT NOT NULL
                    )""",
                    """
                    CREATE TABLE IF NOT EXISTS custom_skins (
                      name VARCHAR(64) PRIMARY KEY,
                      skin_id BIGINT NOT NULL,
                      display_name VARCHAR(128)
                    )"""
            };
            case MYSQL -> new String[]{
                    """
                    CREATE TABLE IF NOT EXISTS skins (
                      id BIGINT PRIMARY KEY AUTO_INCREMENT,
                      texture_value TEXT NOT NULL,
                      texture_signature TEXT,
                      variant VARCHAR(16) NOT NULL,
                      source VARCHAR(32) NOT NULL,
                      source_key VARCHAR(255) NOT NULL,
                      created_at BIGINT NOT NULL,
                      UNIQUE KEY uq_skins (source, source_key, variant)
                    )""",
                    """
                    CREATE TABLE IF NOT EXISTS players (
                      uuid VARCHAR(36) PRIMARY KEY,
                      skin_id BIGINT,
                      original_value TEXT,
                      original_signature TEXT,
                      updated_at BIGINT NOT NULL
                    )""",
                    """
                    CREATE TABLE IF NOT EXISTS custom_skins (
                      name VARCHAR(64) PRIMARY KEY,
                      skin_id BIGINT NOT NULL,
                      display_name VARCHAR(128)
                    )"""
            };
            case POSTGRESQL -> new String[]{
                    """
                    CREATE TABLE IF NOT EXISTS skins (
                      id BIGSERIAL PRIMARY KEY,
                      texture_value TEXT NOT NULL,
                      texture_signature TEXT,
                      variant VARCHAR(16) NOT NULL,
                      source VARCHAR(32) NOT NULL,
                      source_key VARCHAR(255) NOT NULL,
                      created_at BIGINT NOT NULL,
                      UNIQUE (source, source_key, variant)
                    )""",
                    """
                    CREATE TABLE IF NOT EXISTS players (
                      uuid VARCHAR(36) PRIMARY KEY,
                      skin_id BIGINT,
                      original_value TEXT,
                      original_signature TEXT,
                      updated_at BIGINT NOT NULL
                    )""",
                    """
                    CREATE TABLE IF NOT EXISTS custom_skins (
                      name VARCHAR(64) PRIMARY KEY,
                      skin_id BIGINT NOT NULL,
                      display_name VARCHAR(128)
                    )"""
            };
        };

        try (Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                statement.execute(sql);
            }
        }
    }

    @Override
    public synchronized long saveSkin(SkinData data) {
        String variant = data.variant() == null ? SkinVariant.CLASSIC.model() : data.variant().model();
        String source = data.source().id();
        String key = data.sourceKey() == null ? "" : data.sourceKey();
        try {
            Optional<Long> existing = skinId(source, key, variant);
            if (existing.isPresent()) {
                long id = existing.get();
                try (Connection connection = dataSource.getConnection();
                     PreparedStatement statement = connection.prepareStatement(
                             "UPDATE skins SET texture_value = ?, texture_signature = ? WHERE id = ?")) {
                    statement.setString(1, data.value());
                    statement.setString(2, data.signature());
                    statement.setLong(3, id);
                    statement.executeUpdate();
                }
                return id;
            }
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "INSERT INTO skins (texture_value, texture_signature, variant, source, source_key, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                         Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, data.value());
                statement.setString(2, data.signature());
                statement.setString(3, variant);
                statement.setString(4, source);
                statement.setString(5, key);
                statement.setLong(6, data.createdAt() > 0 ? data.createdAt() : System.currentTimeMillis() / 1000L);
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (keys.next()) {
                        return keys.getLong(1);
                    }
                }
            }
        } catch (SQLException e) {
            throw new StorageException("No se pudo guardar la skin", e);
        }
        throw new StorageException("No se pudo obtener el identificador de la skin guardada");
    }

    private Optional<Long> skinId(String source, String key, String variant) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT id FROM skins WHERE source = ? AND source_key = ? AND variant = ?")) {
            statement.setString(1, source);
            statement.setString(2, key);
            statement.setString(3, variant);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return Optional.of(result.getLong(1));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<SkinData> skinById(long id) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT id, texture_value, texture_signature, variant, source, source_key, created_at FROM skins WHERE id = ?")) {
            statement.setLong(1, id);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return Optional.of(mapSkin(result));
                }
            }
        } catch (SQLException e) {
            throw new StorageException("No se pudo leer la skin " + id, e);
        }
        return Optional.empty();
    }

    private SkinData mapSkin(ResultSet result) throws SQLException {
        return new SkinData(
                result.getLong("id"),
                result.getString("texture_value"),
                result.getString("texture_signature"),
                SkinVariant.parseOrDefault(result.getString("variant"), SkinVariant.CLASSIC),
                SkinSource.parse(result.getString("source")),
                result.getString("source_key"),
                result.getLong("created_at"));
    }

    @Override
    public synchronized void setPlayerSkin(UUID uuid, long skinId) {
        long now = System.currentTimeMillis() / 1000L;
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE players SET skin_id = ?, updated_at = ? WHERE uuid = ?")) {
                statement.setLong(1, skinId);
                statement.setLong(2, now);
                statement.setString(3, uuid.toString());
                if (statement.executeUpdate() > 0) {
                    return;
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO players (uuid, skin_id, original_value, original_signature, updated_at) VALUES (?, ?, NULL, NULL, ?)")) {
                statement.setString(1, uuid.toString());
                statement.setLong(2, skinId);
                statement.setLong(3, now);
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            throw new StorageException("No se pudo guardar la seleccion de skin", e);
        }
    }

    @Override
    public synchronized void clearPlayerSkin(UUID uuid) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE players SET skin_id = NULL, updated_at = ? WHERE uuid = ?")) {
            statement.setLong(1, System.currentTimeMillis() / 1000L);
            statement.setString(2, uuid.toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("No se pudo borrar la seleccion de skin", e);
        }
    }

    @Override
    public Optional<Long> playerSkinId(UUID uuid) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT skin_id FROM players WHERE uuid = ?")) {
            statement.setString(1, uuid.toString());
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    long id = result.getLong(1);
                    if (!result.wasNull()) {
                        return Optional.of(id);
                    }
                }
            }
        } catch (SQLException e) {
            throw new StorageException("No se pudo leer la skin del jugador", e);
        }
        return Optional.empty();
    }

    @Override
    public synchronized void touchPlayer(UUID uuid) {
        long now = System.currentTimeMillis() / 1000L;
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE players SET updated_at = ? WHERE uuid = ?")) {
                statement.setLong(1, now);
                statement.setString(2, uuid.toString());
                if (statement.executeUpdate() > 0) {
                    return;
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO players (uuid, skin_id, original_value, original_signature, updated_at) VALUES (?, NULL, NULL, NULL, ?)")) {
                statement.setString(1, uuid.toString());
                statement.setLong(2, now);
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            throw new StorageException("No se pudo actualizar el jugador", e);
        }
    }

    @Override
    public synchronized void saveOriginalIfAbsent(UUID uuid, SkinProperty property) {
        if (property == null) {
            return;
        }
        long now = System.currentTimeMillis() / 1000L;
        try (Connection connection = dataSource.getConnection()) {
            boolean exists;
            try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM players WHERE uuid = ?")) {
                statement.setString(1, uuid.toString());
                try (ResultSet result = statement.executeQuery()) {
                    exists = result.next();
                }
            }
            if (exists) {
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE players SET original_value = ?, original_signature = ?, updated_at = ? WHERE uuid = ? AND (original_value IS NULL OR original_value = '')")) {
                    statement.setString(1, property.value());
                    statement.setString(2, property.signature());
                    statement.setLong(3, now);
                    statement.setString(4, uuid.toString());
                    statement.executeUpdate();
                }
            } else {
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO players (uuid, skin_id, original_value, original_signature, updated_at) VALUES (?, NULL, ?, ?, ?)")) {
                    statement.setString(1, uuid.toString());
                    statement.setString(2, property.value());
                    statement.setString(3, property.signature());
                    statement.setLong(4, now);
                    statement.executeUpdate();
                }
            }
        } catch (SQLException e) {
            throw new StorageException("No se pudo guardar la skin original", e);
        }
    }

    @Override
    public Optional<SkinProperty> originalSkin(UUID uuid) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT original_value, original_signature FROM players WHERE uuid = ?")) {
            statement.setString(1, uuid.toString());
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    String value = result.getString(1);
                    if (value != null && !value.isBlank()) {
                        return Optional.of(SkinProperty.textures(value, result.getString(2)));
                    }
                }
            }
        } catch (SQLException e) {
            throw new StorageException("No se pudo leer la skin original", e);
        }
        return Optional.empty();
    }

    @Override
    public Optional<SkinData> customSkin(String name) {
        String key = name == null ? "" : name.toLowerCase(Locale.ROOT);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT s.id, s.texture_value, s.texture_signature, s.variant, s.source, s.source_key, s.created_at "
                             + "FROM custom_skins c JOIN skins s ON s.id = c.skin_id WHERE c.name = ?")) {
            statement.setString(1, key);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return Optional.of(mapSkin(result));
                }
            }
        } catch (SQLException e) {
            throw new StorageException("No se pudo leer la skin personalizada " + name, e);
        }
        return Optional.empty();
    }

    @Override
    public boolean customSkinExists(String name) {
        String key = name == null ? "" : name.toLowerCase(Locale.ROOT);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM custom_skins WHERE name = ?")) {
            statement.setString(1, key);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        } catch (SQLException e) {
            throw new StorageException("No se pudo comprobar la skin personalizada", e);
        }
    }

    @Override
    public synchronized void setCustomSkin(String name, long skinId, String displayName) {
        String key = name.toLowerCase(Locale.ROOT);
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE custom_skins SET skin_id = ?, display_name = ? WHERE name = ?")) {
                update.setLong(1, skinId);
                update.setString(2, displayName);
                update.setString(3, key);
                if (update.executeUpdate() > 0) {
                    return;
                }
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO custom_skins (name, skin_id, display_name) VALUES (?, ?, ?)")) {
                insert.setString(1, key);
                insert.setLong(2, skinId);
                insert.setString(3, displayName);
                insert.executeUpdate();
            }
        } catch (SQLException e) {
            throw new StorageException("No se pudo guardar la skin personalizada", e);
        }
    }

    @Override
    public synchronized boolean setCustomDisplayName(String name, String displayName) {
        String key = name.toLowerCase(Locale.ROOT);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE custom_skins SET display_name = ? WHERE name = ?")) {
            statement.setString(1, displayName);
            statement.setString(2, key);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new StorageException("No se pudo actualizar el nombre visible", e);
        }
    }

    @Override
    public List<String> customSkinNames() {
        List<String> names = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT name FROM custom_skins ORDER BY name");
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                names.add(result.getString(1));
            }
        } catch (SQLException e) {
            throw new StorageException("No se pudieron listar las skins personalizadas", e);
        }
        return names;
    }

    @Override
    public synchronized int purgeOlderThan(long epochSeconds) {
        int removed = 0;
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM players WHERE updated_at < ? AND skin_id IS NULL")) {
                statement.setLong(1, epochSeconds);
                removed += statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM skins WHERE created_at < ? "
                            + "AND id NOT IN (SELECT skin_id FROM players WHERE skin_id IS NOT NULL) "
                            + "AND id NOT IN (SELECT skin_id FROM custom_skins)")) {
                statement.setLong(1, epochSeconds);
                removed += statement.executeUpdate();
            }
        } catch (SQLException e) {
            throw new StorageException("No se pudieron purgar los datos antiguos", e);
        }
        return removed;
    }

    @Override
    public long countSkins() {
        return count("SELECT COUNT(*) FROM skins");
    }

    @Override
    public long countPlayers() {
        return count("SELECT COUNT(*) FROM players");
    }

    private long count(String sql) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            if (result.next()) {
                return result.getLong(1);
            }
        } catch (SQLException e) {
            throw new StorageException("No se pudo contar registros", e);
        }
        return 0L;
    }

    @Override
    public String type() {
        return settings.type().name().toLowerCase(Locale.ROOT);
    }

    @Override
    public void close() {
        if (dataSource != null) {
            try {
                dataSource.close();
            } catch (RuntimeException e) {
                logger.log(Level.WARNING, "Error cerrando el pool de conexiones", e);
            }
            dataSource = null;
        }
    }
}
