package dev.starfall.tab;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.*;
import java.util.regex.*;

/**
 * Starfall player list header, footer, sorting and prefixes.
 * Does not modify scoreboard teams or in-world nametags.
 * All Bukkit calls are synchronous.
 */
public final class StarfallTabPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private static final Pattern HEX = Pattern.compile("&#([0-9a-fA-F]{6})");
    private final Map<UUID, RenderCache> rendered = new HashMap<>();
    private final Map<UUID, Original> originals = new HashMap<>();
    private List<Group> groups = List.of();
    private Group fallback;
    private BukkitTask refreshTask;
    private boolean refreshQueued;
    private Method papiMethod;
    private Method lpGet, lpUserManager, lpUser, lpInherited, lpGroupName;
    private Object lpOptions;

    @Override public void onEnable() {
        saveDefaultConfig();
        PluginCommand cmd = Objects.requireNonNull(getCommand("starfalltab"));
        cmd.setExecutor(this);
        cmd.setTabCompleter(this);
        Bukkit.getPluginManager().registerEvents(this, this);
        detectIntegrations();
        if (Bukkit.getPluginManager().isPluginEnabled("TAB"))
            getLogger().warning("Another TAB plugin detected. Disable its header/footer, sorting and player-list-name features to prevent conflicts.");
        reloadPlugin();
    }

    @Override public void onDisable() {
        if (refreshTask != null) refreshTask.cancel();
        restoreOriginals();
        rendered.clear();
        originals.clear();
    }

    private void detectIntegrations() {
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                Class<?> api = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
                papiMethod = api.getMethod("setPlaceholders", OfflinePlayer.class, String.class);
                getLogger().info("PlaceholderAPI integration active.");
            } catch (ReflectiveOperationException e) {
                getLogger().warning("PlaceholderAPI could not be initialized: " + e.getMessage());
            }
        }
        if (Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
            try {
                Class<?> provider = Class.forName("net.luckperms.api.LuckPermsProvider");
                Class<?> api = Class.forName("net.luckperms.api.LuckPerms");
                Class<?> mgr = Class.forName("net.luckperms.api.model.user.UserManager");
                Class<?> user = Class.forName("net.luckperms.api.model.user.User");
                Class<?> opts = Class.forName("net.luckperms.api.query.QueryOptions");
                Class<?> group = Class.forName("net.luckperms.api.model.group.Group");
                lpGet = provider.getMethod("get");
                lpUserManager = api.getMethod("getUserManager");
                lpUser = mgr.getMethod("getUser", UUID.class);
                lpInherited = user.getMethod("getInheritedGroups", opts);
                lpGroupName = group.getMethod("getName");
                lpOptions = opts.getMethod("nonContextual").invoke(null);
                getLogger().info("LuckPerms inherited-group detection active.");
            } catch (ReflectiveOperationException e) {
                lpGet = null;
                getLogger().warning("LuckPerms could not be initialized: " + e.getMessage());
            }
        }
    }

    private void reloadPlugin() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
        reloadConfig();
        readGroups();
        if (!getConfig().getBoolean("enabled", true)) {
            restoreOriginals();
            rendered.clear();
            originals.clear();
            getLogger().info("StarfallTab disabled in config.");
            return;
        }
        rendered.clear();
        int ticks = Math.max(20, Math.min(1200, getConfig().getInt("refresh-ticks", 40)));
        refreshTask = Bukkit.getScheduler().runTaskTimer(this, this::refreshAll, 1L, ticks);
        getLogger().info("StarfallTab refresh interval: " + ticks + " ticks.");
    }

    private void readGroups() {
        List<Group> all = new ArrayList<>();
        ConfigurationSection parent = getConfig().getConfigurationSection("groups");
        if (parent != null) for (String id : parent.getKeys(false)) {
            ConfigurationSection value = parent.getConfigurationSection(id);
            if (value == null) continue;
            Set<String> names = new HashSet<>();
            for (String name : value.getStringList("luckperms-groups"))
                if (!name.isBlank()) names.add(name.toLowerCase(Locale.ROOT));
            all.add(new Group(id, value.getInt("priority", 999),
                value.getString("permission", "").trim(), Set.copyOf(names),
                value.getString("prefix", "&7[PLAYER] &f")));
        }
        all.sort(Comparator.comparingInt(Group::priority).thenComparing(Group::id));
        groups = List.copyOf(all);
        fallback = groups.stream().filter(g -> g.id.equalsIgnoreCase("default"))
            .findFirst().orElse(new Group("default", 999, "", Set.of(), "&7[PLAYER] &f"));
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        rendered.remove(id);
        originals.remove(id);
        if (refreshTask != null && !refreshQueued) {
            refreshQueued = true;
            Bukkit.getScheduler().runTask(this, () -> {
                refreshQueued = false;
                if (refreshTask != null) refreshAll();
            });
        }
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        rendered.remove(id);
        originals.remove(id);
    }

    private void refreshAll() {
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (online.isEmpty()) return;
        Map<UUID, Group> assigned = new HashMap<>();
        for (Player player : online) {
            assigned.put(player.getUniqueId(), findGroup(player));
            originals.computeIfAbsent(player.getUniqueId(), id ->
                new Original(player.getPlayerListName(), player.getPlayerListHeader(),
                    player.getPlayerListFooter(), player.getPlayerListOrder()));
        }

        online.sort(Comparator.comparingInt((Player p) -> assigned.get(p.getUniqueId()).priority)
            .thenComparing(p -> p.getName().toLowerCase(Locale.ROOT)));

        int order = 1;
        for (Player target : online) {
            Group group = assigned.get(target.getUniqueId());
            String name = color(expand(target, getConfig().getString("name-format", "%prefix%&f%player%"), group));
            RenderCache old = rendered.computeIfAbsent(target.getUniqueId(), id -> new RenderCache());
            if (!Objects.equals(old.name, name)) {
                target.setPlayerListName(name);
                old.name = name;
            }
            if (old.order != order) {
                target.setPlayerListOrder(order);
                old.order = order;
            }
            order++;
        }

        String headerTemplate = String.join("\n", getConfig().getStringList("header"));
        String footerTemplate = String.join("\n", getConfig().getStringList("footer"));
        for (Player viewer : online) {
            Group group = assigned.get(viewer.getUniqueId());
            String header = color(expand(viewer, headerTemplate, group));
            String footer = color(expand(viewer, footerTemplate, group));
            RenderCache old = rendered.get(viewer.getUniqueId());
            if (!Objects.equals(old.header, header) || !Objects.equals(old.footer, footer)) {
                viewer.setPlayerListHeaderFooter(header, footer);
                old.header = header;
                old.footer = footer;
            }
        }
    }

    private Group findGroup(Player player) {
        Set<String> inherited = getLuckPermsGroups(player);
        for (Group group : groups) {
            if (group.id.equalsIgnoreCase("default")) continue;
            if (!group.permission.isEmpty() && player.hasPermission(group.permission)) return group;
            for (String name : group.lpNames) if (inherited.contains(name)) return group;
        }
        return fallback;
    }

    private Set<String> getLuckPermsGroups(Player player) {
        if (lpGet == null) return Set.of();
        try {
            Object api = lpGet.invoke(null);
            Object manager = lpUserManager.invoke(api);
            Object user = lpUser.invoke(manager, player.getUniqueId());
            if (user == null) return Set.of();
            Object result = lpInherited.invoke(user, lpOptions);
            if (!(result instanceof Collection<?> collection)) return Set.of();
            Set<String> names = new HashSet<>();
            for (Object group : collection)
                names.add(((String) lpGroupName.invoke(group)).toLowerCase(Locale.ROOT));
            return names;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return Set.of();
        }
    }

    private String expand(Player player, String raw, Group group) {
        String result = raw.replace("%logo%", getConfig().getString("logo", "&b&lSTARFALL"))
            .replace("%prefix%", group.prefix).replace("%player%", player.getName())
            .replace("%rank%", group.id.toUpperCase(Locale.ROOT))
            .replace("%online%", Integer.toString(Bukkit.getOnlinePlayers().size()))
            .replace("%max_players%", Integer.toString(Bukkit.getMaxPlayers()))
            .replace("%ping%", Integer.toString(Math.max(0, player.getPing())))
            .replace("%world%", player.getWorld().getName());
        if (papiMethod != null) try {
            Object replaced = papiMethod.invoke(null, player, result);
            if (replaced instanceof String value) result = value;
        } catch (ReflectiveOperationException | RuntimeException ignored) {}
        return result;
    }

    private String color(String input) {
        Matcher matcher = HEX.matcher(input);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            StringBuilder replacement = new StringBuilder("\u00A7x");
            for (char ch : matcher.group(1).toCharArray()) replacement.append('\u00A7').append(ch);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement.toString()));
        }
        matcher.appendTail(sb);
        return ChatColor.translateAlternateColorCodes('&', sb.toString());
    }

    private void restoreOriginals() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Original was = originals.get(player.getUniqueId());
            RenderCache own = rendered.get(player.getUniqueId());
            if (was == null || own == null) continue;
            try {
                if (Objects.equals(player.getPlayerListName(), own.name))
                    player.setPlayerListName(was.name);
                if (Objects.equals(player.getPlayerListHeader(), own.header)
                        && Objects.equals(player.getPlayerListFooter(), own.footer))
                    player.setPlayerListHeaderFooter(was.header, was.footer);
                if (was.order > 0 && player.getPlayerListOrder() == own.order)
                    player.setPlayerListOrder(was.order);
            } catch (RuntimeException ex) {
                getLogger().warning("Could not restore TAB for " + player.getName());
            }
        }
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(color("&bStarfallTab &8| &7Use &f/" + alias + " reload &7or &f/" + alias + " info"));
            return true;
        }
        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("starfalltab.admin")) {
                sender.sendMessage(color("&cNo permission."));
                return true;
            }
            reloadPlugin();
            sender.sendMessage(color("&bStarfallTab &8| &aConfiguration reloaded."));
            return true;
        }
        if (args[0].equalsIgnoreCase("info")) {
            sender.sendMessage(color("&bStarfallTab &8| &7Players: &f" + Bukkit.getOnlinePlayers().size()));
            if (sender instanceof Player player) {
                Group group = findGroup(player);
                sender.sendMessage(color("&7TAB group: &f" + group.id + " &8| &7Priority: &f" + group.priority));
            }
            return true;
        }
        sender.sendMessage(color("&cUse /" + alias + " reload or info."));
        return true;
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length != 1) return List.of();
        return List.of("reload", "info").stream()
            .filter(part -> part.startsWith(args[0].toLowerCase(Locale.ROOT)))
            .filter(part -> !part.equals("reload") || sender.hasPermission("starfalltab.admin"))
            .toList();
    }

    private record Group(String id, int priority, String permission, Set<String> lpNames, String prefix) {}
    private record Original(String name, String header, String footer, int order) {}
    private static final class RenderCache {
        private String name, header, footer;
        private int order = -1;
    }
}
