package dev.yggskins.listener;

import dev.yggskins.YggSkinsPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.entity.Player;

/**
 * Dispara la aplicacion de skins al entrar y limpia el estado al salir.
 */
public final class PlayerJoinListener implements Listener {

    private final YggSkinsPlugin plugin;

    public PlayerJoinListener(YggSkinsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        int delay = plugin.config().applyDelayTicks();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> plugin.skinService().onJoin(player), delay);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        plugin.skinService().onQuit(event.getPlayer());
    }
}
