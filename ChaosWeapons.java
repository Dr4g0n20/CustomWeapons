package com.chaos.weapons;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ChaosWeapons extends JavaPlugin implements CommandExecutor, TabCompleter {

    public static ChaosWeapons inst;

    public Items items;
    /** Server tick counter, advanced by the single central task. */
    public long tick = 0;
    /** True while an item ability is dealing damage itself, so our own damage handlers ignore it. */
    public boolean suppress = false;
    /** Entities with temporary fall damage immunity (entity uuid -> tick it started). */
    public final Map<UUID, Long> immune = new HashMap<>();

    private final Map<String, Long> cooldowns = new HashMap<>();
    private final List<Runnable> tickers = new ArrayList<>();
    private final List<Region> regions = new ArrayList<>();
    private int explosions = 0;
    private CombatListener combat;
    private AbilityListener abilities;

    private static class Region {
        String world;
        double x1, y1, z1, x2, y2, z2;
    }

    @Override
    public void onEnable() {
        inst = this;
        saveDefaultConfig();
        items = new Items(this);
        loadRegions();

        combat = new CombatListener(this);
        abilities = new AbilityListener(this);
        getServer().getPluginManager().registerEvents(combat, this);
        getServer().getPluginManager().registerEvents(abilities, this);

        getCommand("weapon").setExecutor(this);
        getCommand("weapon").setTabCompleter(this);

        // One repeating task drives every timer in the plugin.
        new BukkitRunnable() {
            @Override
            public void run() {
                tick++;
                if (tick % 20 == 0) explosions = 0;
                for (Runnable r : new ArrayList<>(tickers)) {
                    try {
                        r.run();
                    } catch (Exception ex) {
                        getLogger().warning("Ticker error: " + ex);
                        ex.printStackTrace();
                    }
                }
            }
        }.runTaskTimer(this, 1L, 1L);

        getLogger().info("ChaosWeapons enabled with " + items.all().size() + " items.");
    }

    @Override
    public void onDisable() {
        if (combat != null) combat.cleanup();
        if (abilities != null) abilities.cleanup();
    }

    public void addTicker(Runnable r) {
        tickers.add(r);
    }

    // ------------------------------------------------------------------ cooldowns

    /** Returns true and starts the cooldown if the item is ready. */
    public boolean ready(Player p, String id) {
        double secs = getConfig().getDouble("cooldowns." + id, 0);
        if (secs <= 0) return true;
        String key = p.getUniqueId() + ":" + id;
        long now = System.currentTimeMillis();
        Long until = cooldowns.get(key);
        if (until != null && now < until) return false;
        cooldowns.put(key, now + (long) (secs * 1000));
        return true;
    }

    public double remaining(Player p, String id) {
        Long until = cooldowns.get(p.getUniqueId() + ":" + id);
        if (until == null) return 0;
        return Math.max(0, (until - System.currentTimeMillis()) / 1000.0);
    }

    /** Like ready(), but tells the player how long is left when it isn't. */
    public boolean use(Player p, String id) {
        if (ready(p, id)) return true;
        Util.bar(p, ChatColor.RED + "Cooldown: " + String.format("%.1f", remaining(p, id)) + "s");
        return false;
    }

    // ------------------------------------------------------------------ explosions & regions

    public void boom(Location loc, float power) {
        if (explosions >= getConfig().getInt("explosions.max-per-second", 8)) {
            Util.particle(loc, "EXPLOSION_LARGE", 1, 0, 0, 0, 0);
            return;
        }
        explosions++;
        boolean breaks = getConfig().getBoolean("explosions.block-damage", true) && !isProtected(loc);
        loc.getWorld().createExplosion(loc.getX(), loc.getY(), loc.getZ(), power, false, breaks);
    }

    private void loadRegions() {
        regions.clear();
        for (String s : getConfig().getStringList("protected-regions")) {
            try {
                String[] p = s.split(",");
                Region r = new Region();
                r.world = p[0].trim();
                double[] v = new double[6];
                for (int i = 0; i < 6; i++) v[i] = Double.parseDouble(p[i + 1].trim());
                r.x1 = Math.min(v[0], v[3]);
                r.x2 = Math.max(v[0], v[3]);
                r.y1 = Math.min(v[1], v[4]);
                r.y2 = Math.max(v[1], v[4]);
                r.z1 = Math.min(v[2], v[5]);
                r.z2 = Math.max(v[2], v[5]);
                regions.add(r);
            } catch (Exception ex) {
                getLogger().warning("Bad protected-regions entry: " + s);
            }
        }
    }

    public boolean isProtected(Location l) {
        if (l == null || l.getWorld() == null) return false;
        for (Region r : regions) {
            if (!r.world.equals(l.getWorld().getName())) continue;
            if (l.getX() >= r.x1 && l.getX() <= r.x2 && l.getY() >= r.y1 && l.getY() <= r.y2
                    && l.getZ() >= r.z1 && l.getZ() <= r.z2) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ command

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (a.length == 0) {
            s.sendMessage(ChatColor.GOLD + "/weapon give <id> [player] | list | info <id> | guide | reload");
            return true;
        }
        switch (a[0].toLowerCase()) {
            case "list": {
                s.sendMessage(ChatColor.GOLD + "Items:");
                for (Items.Def d : items.all()) {
                    s.sendMessage(d.cat.color + " " + d.id + ChatColor.GRAY + " - " + d.name);
                }
                return true;
            }
            case "info": {
                if (a.length < 2) {
                    s.sendMessage(ChatColor.RED + "Usage: /weapon info <id>");
                    return true;
                }
                Items.Def d = items.get(a[1].toLowerCase());
                if (d == null) {
                    s.sendMessage(ChatColor.RED + "Unknown item. Try /weapon list");
                    return true;
                }
                s.sendMessage(d.cat.color + "" + ChatColor.BOLD + d.name + ChatColor.GRAY + " (" + d.cat.label + ")");
                s.sendMessage(ChatColor.WHITE + d.info);
                return true;
            }
            case "guide": {
                if (!(s instanceof Player)) {
                    s.sendMessage("Players only.");
                    return true;
                }
                Player p = (Player) s;
                p.getInventory().addItem(items.guide());
                p.sendMessage(ChatColor.GREEN + "Here's the Weapon Guide.");
                return true;
            }
            case "give": {
                if (!s.hasPermission("weapons.give")) {
                    s.sendMessage(ChatColor.RED + "You don't have permission.");
                    return true;
                }
                if (a.length < 2) {
                    s.sendMessage(ChatColor.RED + "Usage: /weapon give <id> [player]");
                    return true;
                }
                String id = a[1].toLowerCase();
                if (items.get(id) == null) {
                    s.sendMessage(ChatColor.RED + "Unknown item. Try /weapon list");
                    return true;
                }
                Player target;
                if (a.length >= 3) {
                    if (!s.hasPermission("weapons.admin")) {
                        s.sendMessage(ChatColor.RED + "Only admins can give items to others.");
                        return true;
                    }
                    target = Bukkit.getPlayerExact(a[2]);
                    if (target == null) {
                        s.sendMessage(ChatColor.RED + "Player not found.");
                        return true;
                    }
                } else if (s instanceof Player) {
                    target = (Player) s;
                } else {
                    s.sendMessage("Specify a player.");
                    return true;
                }
                items.give(target, id);
                s.sendMessage(ChatColor.GREEN + "Gave " + items.get(id).name + " to " + target.getName() + ".");
                return true;
            }
            case "reload": {
                if (!s.hasPermission("weapons.admin")) {
                    s.sendMessage(ChatColor.RED + "You don't have permission.");
                    return true;
                }
                reloadConfig();
                loadRegions();
                s.sendMessage(ChatColor.GREEN + "ChaosWeapons config reloaded.");
                return true;
            }
            default:
                s.sendMessage(ChatColor.GOLD + "/weapon give <id> [player] | list | info <id> | guide | reload");
                return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String alias, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            for (String o : new String[]{"give", "list", "info", "guide", "reload"}) {
                if (o.startsWith(a[0].toLowerCase())) out.add(o);
            }
        } else if (a.length == 2 && (a[0].equalsIgnoreCase("give") || a[0].equalsIgnoreCase("info"))) {
            for (Items.Def d : items.all()) {
                if (d.id.startsWith(a[1].toLowerCase())) out.add(d.id);
            }
        } else if (a.length == 3 && a[0].equalsIgnoreCase("give")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(a[2].toLowerCase())) out.add(p.getName());
            }
        }
        return out;
    }
}
