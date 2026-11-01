package dev.yggskins.config;

import dev.yggskins.util.Text;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * Carga y formatea los mensajes localizados.
 */
public final class Messages {

    private final JavaPlugin plugin;
    private final Config config;
    private final Set<String> warned = new HashSet<>();
    private YamlConfiguration file;
    private String prefix = "";

    public Messages(JavaPlugin plugin, Config config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void load() {
        String locale = config.locale();
        String resource = "messages_" + locale + ".yml";
        File folder = plugin.getDataFolder();
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("No se pudo crear la carpeta de datos del plugin.");
        }

        File target = new File(folder, resource);
        if (!target.exists()) {
            try {
                plugin.saveResource(resource, false);
            } catch (IllegalArgumentException e) {
                resource = "messages_en.yml";
                target = new File(folder, resource);
                if (!target.exists()) {
                    plugin.saveResource(resource, false);
                }
            }
        }

        this.file = YamlConfiguration.loadConfiguration(target);
        InputStream defaults = plugin.getResource(resource);
        if (defaults != null) {
            this.file.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(defaults, StandardCharsets.UTF_8)));
        }
        this.prefix = config.prefix();
        this.warned.clear();
    }

    public String raw(String key) {
        String value = file == null ? null : file.getString(key);
        if (value == null && !warned.contains(key)) {
            warned.add(key);
            plugin.getLogger().warning("Falta el mensaje '" + key + "' en el archivo de idioma.");
        }
        return value;
    }

    public String format(String key, Object... placeholders) {
        String value = raw(key);
        if (value == null) {
            return "";
        }
        String result = value.replace("<prefix>", prefix);
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            String name = String.valueOf(placeholders[i]);
            String replacement = placeholders[i + 1] == null ? "" : String.valueOf(placeholders[i + 1]);
            result = result.replace("%" + name + "%", replacement);
        }
        return result;
    }

    public void send(CommandSender sender, String key, Object... placeholders) {
        String message = format(key, placeholders);
        if (message.isEmpty()) {
            return;
        }
        sender.sendMessage(Text.parse(message));
    }
}
