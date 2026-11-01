package dev.yggskins;

import dev.yggskins.command.AdminCommand;
import dev.yggskins.command.SkinCommand;
import dev.yggskins.config.Config;
import dev.yggskins.config.Messages;
import dev.yggskins.listener.PlayerJoinListener;
import dev.yggskins.placeholder.YggSkinsExpansion;
import dev.yggskins.provider.HardcodedSkins;
import dev.yggskins.skin.SkinData;
import dev.yggskins.skin.SkinService;
import dev.yggskins.storage.SqlStorage;
import dev.yggskins.storage.Storage;
import dev.yggskins.storage.StorageSettings;
import dev.yggskins.yggdrasil.YggdrasilClient;
import dev.yggskins.yggdrasil.YggdrasilSettings;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;

/**
 * Punto de entrada del plugin.
 */
public final class YggSkinsPlugin extends JavaPlugin {

    private Config config;
    private Messages messages;
    private Storage storage;
    private YggdrasilClient yggdrasilClient;
    private SkinService skinService;
    private Map<String, SkinData> hardcodedSkins = Map.of();
    private ExecutorService executor;

    @Override
    public void onEnable() {
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        this.config = new Config(this);
        this.messages = new Messages(this, config);
        this.messages.load();
        this.hardcodedSkins = loadHardcodedSkins();

        this.storage = new SqlStorage(storageSettings(), getLogger());
        try {
            this.storage.init();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "No se pudo inicializar el almacenamiento. El plugin se desactivara.", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.yggdrasilClient = createYggdrasilClient();
        this.skinService = new SkinService(this);

        registerCommands();
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);
        registerPlaceholders();
        startMetrics();
        logStartup();
    }

    @Override
    public void onDisable() {
        if (storage != null) {
            storage.close();
        }
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    public void reloadEverything() {
        config.reload();
        messages.load();
        hardcodedSkins = loadHardcodedSkins();
        if (yggdrasilClient != null) {
            yggdrasilClient.clearCache();
        }
        yggdrasilClient = createYggdrasilClient();
        skinService = new SkinService(this);
    }

    private YggdrasilClient createYggdrasilClient() {
        return new YggdrasilClient(new YggdrasilSettings(
                config.yggdrasilApiRoot(),
                config.requireSignature(),
                config.verifySignature(),
                config.yggdrasilTimeoutSeconds(),
                config.cacheSeconds()), getLogger());
    }

    private StorageSettings storageSettings() {
        StorageSettings.Type type = StorageSettings.Type.parse(config.storageType());
        return switch (type) {
            case SQLITE -> StorageSettings.sqlite(new File(getDataFolder(), config.sqliteFile()).toPath());
            case MYSQL -> new StorageSettings(type, null,
                    new StorageSettings.Database(config.mysqlHost(), config.mysqlPort(), config.mysqlDatabase(),
                            config.mysqlUsername(), config.mysqlPassword()), null);
            case POSTGRESQL -> new StorageSettings(type, null, null,
                    new StorageSettings.Database(config.postgresHost(), config.postgresPort(), config.postgresDatabase(),
                            config.postgresUsername(), config.postgresPassword()));
        };
    }

    private void registerCommands() {
        if (config.registerSkinCommand()) {
            CommandMap commandMap = getServer().getCommandMap();
            List<String> aliases = config.registerSkinsAlias() ? List.of("skins") : List.of();
            Command skin = new SkinCommand(this, "skin", aliases);
            if (!commandMap.register(getName().toLowerCase(Locale.ROOT), skin)) {
                getLogger().warning("No se pudo registrar /skin: ya existe un comando con ese nombre. "
                        + "Desactiva commands.register-skin-command si usas otro plugin de skins.");
            }
        }
        PluginCommand admin = getCommand("yggskins");
        if (admin != null) {
            admin.setExecutor(new AdminCommand(this));
            admin.setTabCompleter(new AdminCommand(this));
        }
    }

    private void registerPlaceholders() {
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return;
        }
        try {
            new YggSkinsExpansion(this).register();
            getLogger().info("PlaceholderAPI detectado: placeholders de YggSkins registrados.");
        } catch (Throwable e) {
            getLogger().log(Level.WARNING, "No se pudo registrar la expansion de PlaceholderAPI", e);
        }
    }

    private void startMetrics() {
        if (!config.metricsEnabled()) {
            return;
        }
        int id = config.metricsPluginId();
        if (id <= 0) {
            getLogger().warning("metrics.enabled requiere metrics.plugin-id mayor que cero.");
            return;
        }
        try {
            new org.bstats.bukkit.Metrics(this, id);
        } catch (Throwable e) {
            getLogger().log(Level.WARNING, "No se pudo iniciar bStats", e);
        }
    }

    private Map<String, SkinData> loadHardcodedSkins() {
        File file = new File(getDataFolder(), "hardcoded_skins.json");
        if (!file.exists()) {
            saveResource("hardcoded_skins.json", false);
        }
        try {
            return Map.copyOf(new HashMap<>(HardcodedSkins.parse(Files.readString(file.toPath()))));
        } catch (IOException e) {
            getLogger().log(Level.WARNING, "No se pudo leer hardcoded_skins.json", e);
            return Map.of();
        }
    }

    private void logStartup() {
        getLogger().info("YggSkins iniciado con almacenamiento " + storage.type() + ".");
        if (!config.yggdrasilEnabled()) {
            getLogger().info("El proveedor Yggdrasil esta desactivado en config.yml.");
            return;
        }
        String root = yggdrasilClient.apiRoot();
        if (root == null) {
            getLogger().warning("No se detecto authlib-injector. Configura yggdrasil.api-root en config.yml "
                    + "para usar skins del servidor de autenticacion.");
        } else {
            getLogger().info("API Root de Yggdrasil: " + root);
        }
    }

    public Config config() {
        return config;
    }

    public Messages messages() {
        return messages;
    }

    public Storage storage() {
        return storage;
    }

    public YggdrasilClient yggdrasilClient() {
        return yggdrasilClient;
    }

    public SkinService skinService() {
        return skinService;
    }

    public Map<String, SkinData> hardcodedSkins() {
        return hardcodedSkins;
    }

    public ExecutorService executor() {
        return executor;
    }
}
