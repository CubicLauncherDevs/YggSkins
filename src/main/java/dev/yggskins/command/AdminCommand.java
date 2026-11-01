package dev.yggskins.command;

import dev.yggskins.YggSkinsPlugin;
import dev.yggskins.config.Messages;
import dev.yggskins.skin.SkinVariant;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /yggskins: comandos de administracion.
 */
public final class AdminCommand implements TabExecutor {

    private static final List<String> SUBCOMMANDS = List.of(
            "reload", "status", "info", "applyskin", "drop", "createcustom", "setcustomname", "purgeolddata", "help");

    private final YggSkinsPlugin plugin;

    public AdminCommand(YggSkinsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        Messages messages = plugin.messages();
        if (!sender.hasPermission("yggskins.admin")) {
            messages.send(sender, "general.no-permission");
            return true;
        }
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                if (!require(sender, "yggskins.admin.reload")) {
                    return true;
                }
                plugin.reloadEverything();
                plugin.messages().send(sender, "general.reload");
            }
            case "status" -> {
                if (require(sender, "yggskins.admin.status")) {
                    plugin.skinService().status(sender);
                }
            }
            case "info" -> {
                if (!require(sender, "yggskins.admin.info")) {
                    return true;
                }
                if (args.length < 2) {
                    messages.send(sender, "general.unknown-command");
                    return true;
                }
                OfflinePlayer target = resolveTarget(args[1]);
                plugin.skinService().adminInfo(sender, target);
            }
            case "applyskin" -> {
                if (!require(sender, "yggskins.admin.applyskin")) {
                    return true;
                }
                if (args.length < 2) {
                    messages.send(sender, "general.unknown-command");
                    return true;
                }
                Player target = plugin.getServer().getPlayerExact(args[1]);
                if (target == null) {
                    messages.send(sender, "admin.applyskin.offline", "player", args[1]);
                    return true;
                }
                plugin.skinService().adminApply(sender, target);
            }
            case "drop" -> {
                if (!require(sender, "yggskins.admin.drop")) {
                    return true;
                }
                if (args.length < 2) {
                    messages.send(sender, "general.unknown-command");
                    return true;
                }
                plugin.skinService().adminDrop(sender, resolveTarget(args[1]));
            }
            case "createcustom" -> {
                if (!require(sender, "yggskins.admin.createcustom")) {
                    return true;
                }
                if (args.length < 3) {
                    messages.send(sender, "general.unknown-command");
                    return true;
                }
                SkinVariant variant = args.length >= 4 ? SkinVariant.parse(args[3]) : null;
                if (args.length >= 4 && variant == null) {
                    messages.send(sender, "skin.invalid-variant");
                    return true;
                }
                plugin.skinService().createCustom(sender, args[1], args[2], variant);
            }
            case "setcustomname" -> {
                if (!require(sender, "yggskins.admin.createcustom")) {
                    return true;
                }
                if (args.length < 3) {
                    messages.send(sender, "general.unknown-command");
                    return true;
                }
                String display = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
                plugin.skinService().setCustomName(sender, args[1], display);
            }
            case "purgeolddata" -> {
                if (!require(sender, "yggskins.admin.purge")) {
                    return true;
                }
                if (args.length < 2) {
                    messages.send(sender, "admin.purge.invalid");
                    return true;
                }
                try {
                    plugin.skinService().purge(sender, Integer.parseInt(args[1]));
                } catch (NumberFormatException e) {
                    messages.send(sender, "admin.purge.invalid");
                }
            }
            default -> sendHelp(sender);
        }
        return true;
    }

    private OfflinePlayer resolveTarget(String name) {
        Player online = plugin.getServer().getPlayerExact(name);
        if (online != null) {
            return online;
        }
        OfflinePlayer offline = plugin.getServer().getOfflinePlayerIfCached(name);
        if (offline != null) {
            return offline;
        }
        return plugin.getServer().getOfflinePlayer(name);
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
        messages.send(sender, "admin.help.header");
        messages.send(sender, "admin.help.reload");
        messages.send(sender, "admin.help.status");
        messages.send(sender, "admin.help.info");
        messages.send(sender, "admin.help.applyskin");
        messages.send(sender, "admin.help.drop");
        messages.send(sender, "admin.help.createcustom");
        messages.send(sender, "admin.help.setcustomname");
        messages.send(sender, "admin.help.purge");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> candidates = new ArrayList<>();
        if (args.length == 1) {
            candidates.addAll(SUBCOMMANDS);
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("info") || sub.equals("applyskin") || sub.equals("drop")) {
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    candidates.add(player.getName());
                }
            } else if (sub.equals("setcustomname")) {
                candidates.addAll(plugin.skinService().customNames());
            }
        } else if (args.length == 4 && args[0].equalsIgnoreCase("createcustom")) {
            candidates.addAll(List.of("classic", "slim"));
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String candidate : candidates) {
            if (candidate.toLowerCase(Locale.ROOT).startsWith(prefix) && !result.contains(candidate)) {
                result.add(candidate);
            }
        }
        return result;
    }
}
