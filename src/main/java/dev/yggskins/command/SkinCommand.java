package dev.yggskins.command;

import dev.yggskins.YggSkinsPlugin;
import dev.yggskins.config.Messages;
import dev.yggskins.skin.SkinVariant;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /skin y sus alias.
 */
public final class SkinCommand extends Command {

    private static final List<String> VARIANTS = List.of("classic", "slim");

    private final YggSkinsPlugin plugin;

    public SkinCommand(YggSkinsPlugin plugin, String name, List<String> aliases) {
        super(name, "Gestiona tu skin", "/" + name + " help", aliases);
        this.plugin = plugin;
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        Messages messages = plugin.messages();
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return true;
        }
        if (!sender.hasPermission("yggskins.command")) {
            messages.send(sender, "general.no-permission");
            return true;
        }
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help" -> sendHelp(sender);
            case "set" -> {
                if (!require(sender, "yggskins.command.set")) {
                    return true;
                }
                if (args.length < 2) {
                    messages.send(sender, "general.unknown-command");
                    return true;
                }
                SkinVariant variant = parseVariant(sender, args, 2);
                if (variant == null && args.length >= 3) {
                    return true;
                }
                plugin.skinService().setByName(sender, player, args[1], variant);
            }
            case "clear", "reset" -> {
                if (!require(sender, "yggskins.command.clear")) {
                    return true;
                }
                plugin.skinService().clear(sender, player);
            }
            case "update", "refresh" -> {
                if (!require(sender, "yggskins.command.update")) {
                    return true;
                }
                plugin.skinService().refresh(sender, player);
            }
            case "url" -> {
                if (!require(sender, "yggskins.command.url")) {
                    return true;
                }
                if (args.length < 2) {
                    messages.send(sender, "general.unknown-command");
                    return true;
                }
                SkinVariant variant = parseVariant(sender, args, 2);
                if (variant == null && args.length >= 3) {
                    return true;
                }
                plugin.skinService().setUrl(sender, player, args[1], variant);
            }
            case "info" -> plugin.skinService().info(sender, player);
            default -> {
                if (!require(sender, "yggskins.command.set")) {
                    return true;
                }
                SkinVariant variant = parseVariant(sender, args, 1);
                if (variant == null && args.length >= 2) {
                    return true;
                }
                plugin.skinService().setByName(sender, player, args[0], variant);
            }
        }
        return true;
    }

    private SkinVariant parseVariant(CommandSender sender, String[] args, int index) {
        if (args.length <= index) {
            return null;
        }
        SkinVariant variant = SkinVariant.parse(args[index]);
        if (variant == null) {
            plugin.messages().send(sender, "skin.invalid-variant");
        }
        return variant;
    }

    private boolean require(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) {
            return true;
        }
        plugin.messages().send(sender, "general.no-permission");
        return false;
    }

    private void sendHelp(CommandSender sender) {
        Messages messages = plugin.messages();
        messages.send(sender, "help.header");
        messages.send(sender, "help.set");
        messages.send(sender, "help.clear");
        messages.send(sender, "help.update");
        messages.send(sender, "help.url");
        messages.send(sender, "help.info");
        if (sender.hasPermission("yggskins.admin")) {
            messages.send(sender, "help.admin");
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        List<String> candidates = new ArrayList<>();
        if (args.length == 1) {
            candidates.addAll(List.of("set", "clear", "update", "url", "info", "help"));
            candidates.addAll(onlineNames());
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("set")) {
                candidates.addAll(onlineNames());
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
            candidates.addAll(VARIANTS);
        }
        return filter(candidates, args[args.length - 1]);
    }

    private List<String> onlineNames() {
        List<String> names = new ArrayList<>();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            names.add(player.getName());
        }
        return names;
    }

    private static List<String> filter(List<String> candidates, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String candidate : candidates) {
            if (candidate.toLowerCase(Locale.ROOT).startsWith(lower) && !result.contains(candidate)) {
                result.add(candidate);
            }
        }
        return result;
    }
}
