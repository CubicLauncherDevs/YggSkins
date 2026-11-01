package dev.yggskins.skin;

import dev.yggskins.YggSkinsPlugin;
import dev.yggskins.config.Config;
import dev.yggskins.config.Messages;
import dev.yggskins.provider.CustomProvider;
import dev.yggskins.provider.MineSkinProvider;
import dev.yggskins.provider.MojangProvider;
import dev.yggskins.provider.SkinProvider;
import dev.yggskins.provider.SkinProviderException;
import dev.yggskins.provider.YggdrasilProvider;
import dev.yggskins.storage.Storage;
import dev.yggskins.yggdrasil.YggdrasilClient;
import dev.yggskins.yggdrasil.YggdrasilProfile;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * Orquesta la resolucion, persistencia y aplicacion de skins.
 */
public final class SkinService {

    private final YggSkinsPlugin plugin;
    private final Config config;
    private final Messages messages;
    private final Storage storage;
    private final YggdrasilClient yggdrasil;

    private final Map<String, SkinProvider> providers = new LinkedHashMap<>();
    private List<SkinProvider> ordered = List.of();

    private YggdrasilProvider yggdrasilProvider;
    private MojangProvider mojangProvider;
    private MineSkinProvider mineSkinProvider;
    private CustomProvider customProvider;

    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, SkinData> current = new ConcurrentHashMap<>();
    private final Set<UUID> loading = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean warnedAboutSignature = new AtomicBoolean();

    private SkinCache<String, SkinData> resignedCache;

    public SkinService(YggSkinsPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.config();
        this.messages = plugin.messages();
        this.storage = plugin.storage();
        this.yggdrasil = plugin.yggdrasilClient();
        buildProviders();
    }

    public void buildProviders() {
        providers.clear();
        yggdrasilProvider = new YggdrasilProvider(yggdrasil);
        mojangProvider = new MojangProvider(config.mojangEnabled(), config.yggdrasilTimeoutSeconds());
        mineSkinProvider = new MineSkinProvider(
                config.mineskinEnabled(),
                config.mineskinApiKey(),
                config.mineskinVisibility(),
                config.allowUnsignedFallback(),
                config.allowedDomains(),
                config.mineskinTimeoutSeconds());
        customProvider = new CustomProvider(storage, yggdrasilProvider, plugin.hardcodedSkins());

        providers.put("yggdrasil", yggdrasilProvider);
        providers.put("custom", customProvider);
        providers.put("mojang", mojangProvider);
        providers.put("mineskin", mineSkinProvider);

        List<SkinProvider> result = new ArrayList<>();
        for (String name : config.providerOrder()) {
            SkinProvider provider = providers.get(name.toLowerCase(Locale.ROOT));
            if (provider != null && !result.contains(provider)) {
                result.add(provider);
            }
        }
        for (SkinProvider provider : providers.values()) {
            if (!result.contains(provider)) {
                result.add(provider);
            }
        }
        this.ordered = List.copyOf(result);
        this.resignedCache = new SkinCache<>(config.cacheSeconds());
        this.current.clear();
        this.loading.clear();
    }

    public List<String> providerNames() {
        List<String> names = new ArrayList<>();
        for (SkinProvider provider : ordered) {
            if (provider.available()) {
                names.add(provider.name());
            }
        }
        return names;
    }

    // ------------------------------------------------------------------
    // Ciclo de vida del jugador
    // ------------------------------------------------------------------

    public void onJoin(Player player) {
        if (!player.isOnline()) {
            return;
        }
        UUID uuid = player.getUniqueId();
        SkinProperty original = SkinApplier.current(player);
        async(() -> {
            try {
                storage.touchPlayer(uuid);
                if (original != null) {
                    storage.saveOriginalIfAbsent(uuid, original);
                }
                SkinData skin = storedSkin(uuid);
                if (skin == null && !config.defaultSkin().isBlank()) {
                    try {
                        skin = resolve(config.defaultSkin(), null, null);
                    } catch (SkinProviderException e) {
                        plugin.getLogger().fine("Skin por defecto no disponible: " + e.getMessage());
                    }
                }
                if (skin == null) {
                    return;
                }
                if (config.refreshOnJoin()) {
                    SkinData refreshed = refetch(skin);
                    if (refreshed != null) {
                        skin = merge(skin, refreshed);
                        storage.saveSkin(skin);
                    }
                }
                SkinData target = skin;
                sync(() -> {
                    if (player.isOnline()) {
                        applyTo(player, target);
                    }
                });
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "No se pudo aplicar la skin al entrar " + player.getName(), e);
            }
        });
    }

    public void onQuit(Player player) {
        UUID uuid = player.getUniqueId();
        current.remove(uuid);
        loading.remove(uuid);
    }

    // ------------------------------------------------------------------
    // Comandos de jugador
    // ------------------------------------------------------------------

    public void setByName(CommandSender sender, Player player, String name, SkinVariant variant) {
        if (!checkCooldown(sender, player)) {
            return;
        }
        async(() -> {
            SkinData skin;
            try {
                skin = resolve(name, variant, null);
            } catch (SkinProviderException e) {
                sendProviderError(sender, e, name);
                return;
            }
            if (skin == null) {
                reply(sender, "skin.not-found", "name", name);
                return;
            }
            applyAndStore(sender, player, skin, name, "skin.applied");
        });
    }

    public void setUrl(CommandSender sender, Player player, String url, SkinVariant variant) {
        if (!checkCooldown(sender, player)) {
            return;
        }
        reply(sender, "skin.url.creating");
        async(() -> {
            SkinData skin;
            try {
                skin = mineSkinProvider.fetch(url, variant);
            } catch (SkinProviderException e) {
                sendProviderError(sender, e, url);
                return;
            }
            if (skin == null) {
                reply(sender, "skin.url.failed", "reason", "sin datos");
                return;
            }
            applyAndStore(sender, player, skin, url, "skin.url.applied");
        });
    }

    public void clear(CommandSender sender, Player player) {
        async(() -> {
            UUID uuid = player.getUniqueId();
            SkinProperty original;
            try {
                storage.clearPlayerSkin(uuid);
                current.remove(uuid);
                original = storage.originalSkin(uuid).orElse(null);
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "No se pudo limpiar la skin de " + player.getName(), e);
                reply(sender, "general.error", "reason", e.getMessage());
                return;
            }
            if (original == null) {
                try {
                    YggdrasilProfile profile = yggdrasil.profile(uuid);
                    if (profile != null) {
                        original = profile.textures();
                    }
                } catch (Exception ignored) {
                    // Sin respaldo del servidor de autenticacion.
                }
            }
            SkinProperty target = original;
            sync(() -> {
                if (!player.isOnline()) {
                    return;
                }
                if (target != null) {
                    SkinApplier.apply(player, target);
                    messages.send(sender, "skin.cleared");
                } else {
                    SkinApplier.strip(player);
                    messages.send(sender, "skin.cleared-no-original");
                }
            });
        });
    }

    public void refresh(CommandSender sender, Player player) {
        async(() -> {
            UUID uuid = player.getUniqueId();
            SkinData stored = storedSkin(uuid);
            if (stored == null) {
                reply(sender, "skin.update-no-skin");
                return;
            }
            SkinData refreshed = refetch(stored);
            if (refreshed == null) {
                reply(sender, "skin.not-found", "name", stored.sourceKey());
                return;
            }
            SkinData merged = merge(stored, refreshed);
            storage.saveSkin(merged);
            sync(() -> {
                if (player.isOnline()) {
                    applyTo(player, merged);
                    messages.send(sender, "skin.update-done", "source", stored.source().id());
                }
            });
        });
    }

    public void info(CommandSender sender, Player player) {
        async(() -> {
            SkinData skin = storedSkin(player.getUniqueId());
            sync(() -> sendInfo(sender, player.getName(), skin));
        });
    }

    // ------------------------------------------------------------------
    // Comandos de administracion
    // ------------------------------------------------------------------

    public void adminInfo(CommandSender sender, OfflinePlayer target) {
        async(() -> {
            SkinData skin = storedSkin(target.getUniqueId());
            sync(() -> sendInfo(sender, target.getName() == null ? target.getUniqueId().toString() : target.getName(), skin));
        });
    }

    public void adminApply(CommandSender sender, Player target) {
        async(() -> {
            SkinData skin = storedSkin(target.getUniqueId());
            if (skin == null) {
                reply(sender, "admin.info.none", "player", target.getName());
                return;
            }
            sync(() -> {
                if (!target.isOnline()) {
                    messages.send(sender, "admin.applyskin.offline", "player", target.getName());
                    return;
                }
                applyTo(target, skin);
                messages.send(sender, "admin.applyskin.done", "player", target.getName());
            });
        });
    }

    public void adminDrop(CommandSender sender, OfflinePlayer target) {
        async(() -> {
            storage.clearPlayerSkin(target.getUniqueId());
            current.remove(target.getUniqueId());
            Player online = target.isOnline() ? target.getPlayer() : null;
            if (online == null) {
                reply(sender, "admin.drop.offline", "player", target.getName());
                return;
            }
            SkinProperty original = storage.originalSkin(target.getUniqueId()).orElse(null);
            sync(() -> {
                if (original != null) {
                    SkinApplier.apply(online, original);
                } else {
                    SkinApplier.strip(online);
                }
                messages.send(sender, "admin.drop.done", "player", target.getName());
            });
        });
    }

    public void createCustom(CommandSender sender, String name, String source, SkinVariant variant) {
        async(() -> {
            String key = name.toLowerCase(Locale.ROOT);
            if (storage.customSkinExists(key)) {
                reply(sender, "admin.createcustom.exists", "name", key);
                return;
            }
            try {
                SkinData skin;
                if (source.startsWith("http://") || source.startsWith("https://")) {
                    skin = mineSkinProvider.fetch(source, variant);
                } else {
                    String target = source.startsWith(CustomProvider.PLAYER_PREFIX)
                            ? source
                            : CustomProvider.PLAYER_PREFIX + source;
                    skin = customProvider.fetch(target, variant);
                }
                if (skin == null) {
                    reply(sender, "admin.createcustom.failed", "reason", "sin datos");
                    return;
                }
                skin = resignIfNeeded(skin);
                long id = storage.saveSkin(skin);
                storage.setCustomSkin(key, id, key);
                reply(sender, "admin.createcustom.done", "name", key);
            } catch (SkinProviderException e) {
                reply(sender, "admin.createcustom.failed",
                        "reason", e.detail() == null ? e.messageKey() : e.detail());
            }
        });
    }

    public void setCustomName(CommandSender sender, String name, String displayName) {
        async(() -> {
            boolean updated = storage.setCustomDisplayName(name, displayName);
            reply(sender, updated ? "admin.createcustom.setname" : "admin.createcustom.not-found", "name", name);
        });
    }

    public void purge(CommandSender sender, int days) {
        if (days <= 0) {
            reply(sender, "admin.purge.invalid");
            return;
        }
        async(() -> {
            long cutoff = System.currentTimeMillis() / 1000L - (long) days * 86_400L;
            int removed = storage.purgeOlderThan(cutoff);
            reply(sender, "admin.purge.done", "count", removed);
        });
    }

    public void status(CommandSender sender) {
        async(() -> {
            long skins = storage.countSkins();
            long players = storage.countPlayers();
            String root = yggdrasil.apiRoot();
            List<String> available = providerNames();
            sync(() -> {
                messages.send(sender, "admin.status.header");
                messages.send(sender, "admin.status.storage", "type", storage.type());
                messages.send(sender, "admin.status.yggdrasil", "status", yggdrasil.available() ? yes() : no());
                messages.send(sender, "admin.status.detected", "url", root == null ? "-" : root);
                messages.send(sender, "admin.status.skins", "count", skins);
                messages.send(sender, "admin.status.players", "count", players);
                messages.send(sender, "admin.status.providers", "list", available.isEmpty() ? "-" : String.join(", ", available));
            });
        });
    }

    // ------------------------------------------------------------------
    // Consulta para placeholders
    // ------------------------------------------------------------------

    public List<String> customNames() {
        try {
            return storage.customSkinNames();
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    public SkinData currentSkin(UUID uuid) {
        SkinData data = current.get(uuid);
        if (data != null) {
            return data;
        }
        if (loading.add(uuid)) {
            async(() -> {
                try {
                    SkinData stored = storedSkin(uuid);
                    if (stored != null) {
                        current.put(uuid, stored);
                    }
                } catch (RuntimeException e) {
                    plugin.getLogger().log(Level.FINE, "No se pudo cargar la skin para placeholders", e);
                } finally {
                    loading.remove(uuid);
                }
            });
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Interno
    // ------------------------------------------------------------------

    private SkinData resolve(String key, SkinVariant variant, String only) throws SkinProviderException {
        boolean attempted = false;
        SkinProviderException last = null;
        for (SkinProvider provider : ordered) {
            if (only != null && !provider.name().equals(only)) {
                continue;
            }
            if (!provider.available()) {
                continue;
            }
            attempted = true;
            try {
                SkinData data = provider.fetch(key, variant);
                if (data != null) {
                    return resignIfNeeded(data);
                }
            } catch (SkinProviderException e) {
                switch (e.messageKey()) {
                    case "skin.unsigned-rejected", "skin.signature-invalid", "skin.invalid-name" -> throw e;
                    default -> last = e;
                }
            }
        }
        if (!attempted) {
            throw new SkinProviderException("skin.provider-unavailable", null);
        }
        if (last != null) {
            throw last;
        }
        return null;
    }

    /**
     * Las skins de servidores Yggdrasil de terceros no estan firmadas por Mojang, asi que los
     * clientes sin authlib-injector no las muestran. Si esta activado, se re-firman con MineSkin
     * usando la URL de textura original.
     */
    private SkinData resignIfNeeded(SkinData skin) {
        if (skin.source() != SkinSource.YGGDRASIL) {
            return skin;
        }
        if (!config.resignYggdrasil() || !mineSkinProvider.available()) {
            if (!config.resignYggdrasil() && warnedAboutSignature.compareAndSet(false, true)) {
                plugin.getLogger().info("Las skins del servidor de autenticacion no estan firmadas por Mojang: "
                        + "los clientes sin authlib-injector no las veran. Activa "
                        + "providers.mineskin.resign-yggdrasil para re-firmarlas.");
            }
            return skin;
        }
        TextureInfo texture = skin.texture();
        if (texture == null || texture.url() == null || texture.url().isBlank()) {
            return skin;
        }
        SkinData cached = resignedCache.get(texture.url());
        if (cached != null) {
            return copySignature(skin, cached);
        }
        try {
            SkinData resigned = mineSkinProvider.resign(texture.url(), skin.variant());
            if (resigned == null) {
                return skin;
            }
            resignedCache.put(texture.url(), resigned);
            return copySignature(skin, resigned);
        } catch (SkinProviderException e) {
            plugin.getLogger().warning("No se pudo re-firmar con MineSkin la skin " + skin.sourceKey()
                    + ": " + e.getMessage());
            return skin;
        }
    }

    private static SkinData copySignature(SkinData original, SkinData signed) {
        return new SkinData(
                original.id(),
                signed.value(),
                signed.signature(),
                original.variant() != null ? original.variant() : signed.variant(),
                original.source(),
                original.sourceKey(),
                original.createdAt());
    }

    private SkinData refetch(SkinData skin) {
        try {
            return switch (skin.source()) {
                case YGGDRASIL -> resolve(skin.sourceKey(), skin.variant(), "yggdrasil");
                case MOJANG -> resolve(skin.sourceKey(), skin.variant(), "mojang");
                case MINESKIN -> mineSkinProvider.fetch(skin.sourceKey(), skin.variant());
                case CUSTOM, HARDCODED -> customProvider.fetch(skin.sourceKey(), skin.variant());
            };
        } catch (SkinProviderException e) {
            return null;
        }
    }

    private void applyAndStore(CommandSender sender, Player player, SkinData skin, String display, String messageKey) {
        long id = storage.saveSkin(skin);
        SkinData stored = skin.withId(id);
        storage.setPlayerSkin(player.getUniqueId(), id);
        sync(() -> {
            if (!player.isOnline()) {
                return;
            }
            applyTo(player, stored);
            if (stored.signed()) {
                messages.send(sender, messageKey, "skin", display, "variant", variantName(stored), "url", display);
            } else {
                messages.send(sender, "skin.applied-unsigned", "skin", display);
            }
        });
    }

    private void applyTo(Player player, SkinData skin) {
        SkinApplier.apply(player, skin.property());
        current.put(player.getUniqueId(), skin);
    }

    private SkinData storedSkin(UUID uuid) {
        Optional<Long> id = storage.playerSkinId(uuid);
        return id.flatMap(storage::skinById).orElse(null);
    }

    private SkinData merge(SkinData original, SkinData refreshed) {
        return new SkinData(
                original.id(),
                refreshed.value(),
                refreshed.signature(),
                refreshed.variant() == null ? original.variant() : refreshed.variant(),
                original.source(),
                original.sourceKey(),
                original.createdAt());
    }

    private void sendInfo(CommandSender sender, String playerName, SkinData skin) {
        if (skin == null) {
            messages.send(sender, "admin.info.none", "player", playerName);
            return;
        }
        TextureInfo texture = skin.texture();
        messages.send(sender, "admin.info.header", "player", playerName);
        messages.send(sender, "admin.info.skin", "skin", skin.displayName());
        messages.send(sender, "admin.info.source", "source", skin.source().id());
        messages.send(sender, "admin.info.variant", "variant", variantName(skin));
        messages.send(sender, "admin.info.url", "url", texture == null ? "-" : texture.url());
        messages.send(sender, "admin.info.signed", "signed", skin.signed() ? yes() : no());
    }

    private boolean checkCooldown(CommandSender sender, Player player) {
        if (player.hasPermission("yggskins.admin")) {
            return true;
        }
        int seconds = config.cooldownSeconds();
        if (seconds <= 0) {
            return true;
        }
        long now = System.currentTimeMillis();
        Long last = cooldowns.get(player.getUniqueId());
        if (last != null) {
            long remaining = (last + seconds * 1000L - now + 999L) / 1000L;
            if (remaining > 0) {
                messages.send(sender, "skin.cooldown", "seconds", remaining);
                return false;
            }
        }
        cooldowns.put(player.getUniqueId(), now);
        return true;
    }

    private void sendProviderError(CommandSender sender, SkinProviderException e, String fallback) {
        switch (e.messageKey()) {
            case "skin.unsigned-rejected" -> reply(sender, "skin.unsigned-rejected", "name", fallback);
            case "skin.signature-invalid" -> reply(sender, "skin.signature-invalid", "name", fallback);
            case "skin.invalid-name" -> reply(sender, "skin.invalid-name");
            case "skin.url.blocked" -> reply(sender, "skin.url.blocked");
            case "skin.url.invalid" -> reply(sender, "skin.url.invalid", "url", fallback);
            case "skin.url.failed" -> reply(sender, "skin.url.failed", "reason", e.detail());
            case "skin.provider-unavailable" -> reply(sender, "skin.provider-unavailable");
            default -> reply(sender, "general.error", "reason", e.detail() == null ? e.messageKey() : e.detail());
        }
    }

    private String variantName(SkinData skin) {
        return skin.variant() == null ? SkinVariant.CLASSIC.model() : skin.variant().model();
    }

    private String yes() {
        return "es".equals(config.locale()) ? "si" : "yes";
    }

    private String no() {
        return "es".equals(config.locale()) ? "no" : "no";
    }

    private void reply(CommandSender sender, String key, Object... placeholders) {
        if (plugin.getServer().isPrimaryThread()) {
            messages.send(sender, key, placeholders);
        } else {
            sync(() -> messages.send(sender, key, placeholders));
        }
    }

    private void async(Runnable task) {
        plugin.executor().execute(() -> {
            try {
                task.run();
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "Error en una tarea asincrona de skins", e);
            }
        });
    }

    private void sync(Runnable task) {
        if (!plugin.isEnabled()) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, task);
    }
}
