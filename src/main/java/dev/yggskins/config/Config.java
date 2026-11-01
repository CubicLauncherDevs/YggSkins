package dev.yggskins.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;

/**
 * Acceso tipado a config.yml.
 */
public final class Config {

    private final JavaPlugin plugin;
    private FileConfiguration file;

    public Config(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.file = plugin.getConfig();
    }

    private String str(String path, String def) {
        String value = file.getString(path);
        return value == null ? def : value;
    }

    private int integer(String path, int def) {
        return file.getInt(path, def);
    }

    private boolean bool(String path, boolean def) {
        return file.getBoolean(path, def);
    }

    public String locale() {
        return str("locale", "en").toLowerCase(Locale.ROOT);
    }

    public String prefix() {
        return str("prefix", "<gray>[<gold>YggSkins</gold>]</gray> ");
    }

    public boolean yggdrasilEnabled() {
        return bool("yggdrasil.enabled", true);
    }

    public String yggdrasilApiRoot() {
        return str("yggdrasil.api-root", "");
    }

    public boolean requireSignature() {
        return bool("yggdrasil.require-signature", true);
    }

    public boolean verifySignature() {
        return bool("yggdrasil.verify-signature", true);
    }

    public int yggdrasilTimeoutSeconds() {
        return Math.max(1, integer("yggdrasil.timeout-seconds", 10));
    }

    public int cacheSeconds() {
        return Math.max(0, integer("yggdrasil.cache-seconds", 300));
    }

    public List<String> providerOrder() {
        return file.getStringList("providers.order");
    }

    public boolean mojangEnabled() {
        return bool("providers.mojang.enabled", false);
    }

    public boolean mineskinEnabled() {
        return bool("providers.mineskin.enabled", true);
    }

    public String mineskinApiKey() {
        return str("providers.mineskin.api-key", "");
    }

    public String mineskinVisibility() {
        return str("providers.mineskin.visibility", "public");
    }

    public boolean allowUnsignedFallback() {
        return bool("providers.mineskin.allow-unsigned-fallback", true);
    }

    public List<String> allowedDomains() {
        return file.getStringList("providers.mineskin.allowed-domains");
    }

    public int mineskinTimeoutSeconds() {
        return Math.max(1, integer("providers.mineskin.timeout-seconds", 30));
    }

    public boolean resignYggdrasil() {
        return bool("providers.mineskin.resign-yggdrasil", false);
    }

    public String storageType() {
        return str("storage.type", "sqlite").toLowerCase(Locale.ROOT);
    }

    public String sqliteFile() {
        return str("storage.sqlite.file", "yggskins.db");
    }

    public String mysqlHost() {
        return str("storage.mysql.host", "localhost");
    }

    public int mysqlPort() {
        return integer("storage.mysql.port", 3306);
    }

    public String mysqlDatabase() {
        return str("storage.mysql.database", "yggskins");
    }

    public String mysqlUsername() {
        return str("storage.mysql.username", "root");
    }

    public String mysqlPassword() {
        return str("storage.mysql.password", "");
    }

    public String postgresHost() {
        return str("storage.postgresql.host", "localhost");
    }

    public int postgresPort() {
        return integer("storage.postgresql.port", 5432);
    }

    public String postgresDatabase() {
        return str("storage.postgresql.database", "yggskins");
    }

    public String postgresUsername() {
        return str("storage.postgresql.username", "postgres");
    }

    public String postgresPassword() {
        return str("storage.postgresql.password", "");
    }

    public boolean refreshOnJoin() {
        return bool("skins.refresh-on-join", false);
    }

    public int applyDelayTicks() {
        return Math.max(0, integer("skins.apply-delay-ticks", 1));
    }

    public int cooldownSeconds() {
        return Math.max(0, integer("skins.cooldown-seconds", 30));
    }

    public String defaultSkin() {
        return str("skins.default-skin", "");
    }

    public boolean registerSkinCommand() {
        return bool("commands.register-skin-command", true);
    }

    public boolean registerSkinsAlias() {
        return bool("commands.register-skins-alias", true);
    }

    public boolean metricsEnabled() {
        return bool("metrics.enabled", false);
    }

    public int metricsPluginId() {
        return integer("metrics.plugin-id", 0);
    }
}
