package com.chaos.weapons;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** Right-click abilities and everything that keeps running afterwards. All state is in memory only. */
public class AbilityListener implements Listener {

    private static class Warn {
        Location c;
        long end;
    }

    private static class Shot {
        UUID owner;
        boolean mega;
        long born;
    }

    private static class Zoom {
        UUID whistler;
        List<UUID> who = new ArrayList<>();
        long born;
    }

    private static class Bubble {
        List<UUID> members = new ArrayList<>();
        long born;
        double dx, dz;
    }

    private final ChaosWeapons pl;
    private final Items items;
    private final Random rnd = new Random();

    // Anvil Hammer
    private final Set<UUID> anvils = new HashSet<>();
    private final List<Block> placedAnvils = new ArrayList<>();
    private final List<Warn> warns = new ArrayList<>();
    // Chicken Cannon
    private final Map<UUID, Shot> chickens = new HashMap<>();
    private final Map<UUID, Integer> shots = new HashMap<>();
    private final List<Object[]> pendingChains = new ArrayList<>();
    // Whistle, Bubble Wand
    private final List<Zoom> zooms = new ArrayList<>();
    private final List<Bubble> bubbles = new ArrayList<>();
    // Lucky Coin, Fake Death Totem
    private final Map<UUID, Long> coins = new HashMap<>();
    private final Map<UUID, Long> corpses = new HashMap<>();
    private final Map<UUID, UUID> decoyOwner = new HashMap<>();
    private final Map<UUID, Long> decoyEnd = new HashMap<>();
    // Umbrella
    private final Map<UUID, Location> lastPos = new HashMap<>();

    public AbilityListener(ChaosWeapons pl) {
        this.pl = pl;
        this.items = pl.items;
        pl.addTicker(this::tick);
    }

    // ================================================================== ticking

    private void tick() {
        // Anvil warnings: smoke on the ground and a rising whistle until the anvils arrive.
        for (Iterator<Warn> it = warns.iterator(); it.hasNext(); ) {
            Warn w = it.next();
            if (pl.tick > w.end) {
                it.remove();
                continue;
            }
            if (pl.tick % 5 == 0) {
                double[][] corners = {{-5, -5}, {5, -5}, {-5, 5}, {5, 5}, {0, 0}};
                for (double[] c : corners) {
                    Util.particle(w.c.clone().add(c[0], 0.3, c[1]), "SMOKE_LARGE", 6, 0.6, 0.1, 0.6, 0);
                }
                float pitch = 0.5f + (float) (pl.tick - (w.end - 100)) / 100f * 1.5f;
                Util.sound(w.c, "ENTITY_FIREWORK_LAUNCH", 1f, Math.min(2f, Math.max(0.5f, pitch)));
            }
        }

        // Forget anvils that no longer exist.
        if (pl.tick % 100 == 0) {
            for (Iterator<UUID> it = anvils.iterator(); it.hasNext(); ) {
                if (Bukkit.getEntity(it.next()) == null) it.remove();
            }
        }

        tickChickens();
        tickZooms();
        tickBubbles();
        tickMisc();
        tickUmbrella();
    }

    private void tickChickens() {
        for (Iterator<Map.Entry<UUID, Shot>> it = chickens.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Shot> en = it.next();
            Shot s = en.getValue();
            Entity c = Bukkit.getEntity(en.getKey());
            if (c == null || c.isDead()) {
                it.remove();
                continue;
            }
            long age = pl.tick - s.born;
            if (age < 3) continue;
            boolean boom = c.isOnGround() || age > 100 || c.getLocation().getBlock().getType().isSolid();
            boolean reflected = false;
            for (Entity n : c.getNearbyEntities(1.3, 1.3, 1.3)) {
                if (!(n instanceof LivingEntity) || n instanceof Chicken || n instanceof ArmorStand) continue;
                if (n instanceof Player) {
                    Player np = (Player) n;
                    if (np.getUniqueId().equals(s.owner) && age < 10) continue;
                    if (items.isUmbrella(np)) {
                        s.owner = np.getUniqueId();
                        s.born = pl.tick;
                        c.setVelocity(c.getVelocity().multiply(-1.5));
                        Util.sound(np.getLocation(), "ENTITY_ZOMBIE_ATTACK_IRON_DOOR", 1f, 1.5f);
                        reflected = true;
                        break;
                    }
                }
                boom = true;
            }
            if (reflected) continue;
            if (boom) {
                it.remove();
                explode(c, s);
            }
        }
        // Chain chickens are spawned after the loop so we never modify the map while iterating.
        if (!pendingChains.isEmpty()) {
            List<Object[]> todo = new ArrayList<>(pendingChains);
            pendingChains.clear();
            for (Object[] pc : todo) {
                Location loc = (Location) pc[0];
                UUID owner = (UUID) pc[1];
                UUID dead = (UUID) pc[2];
                Player target = null;
                double best = 40 * 40;
                for (Player o : loc.getWorld().getPlayers()) {
                    if (o.getUniqueId().equals(dead) || o.isDead()) continue;
                    double d = o.getLocation().distanceSquared(loc);
                    if (d < best) {
                        best = d;
                        target = o;
                    }
                }
                if (target == null) continue;
                Vector dir = target.getLocation().add(0, 1, 0).toVector().subtract(loc.toVector()).normalize();
                spawnChicken(owner, loc, dir.multiply(1.6), true);
            }
        }
    }

    private void tickZooms() {
        for (Iterator<Zoom> it = zooms.iterator(); it.hasNext(); ) {
            Zoom z = it.next();
            long age = pl.tick - z.born;
            Player w = Bukkit.getPlayer(z.whistler);
            if (age < 40 && w != null) {
                for (UUID id : z.who) {
                    if (id.equals(z.whistler)) continue;
                    Player t = Bukkit.getPlayer(id);
                    if (t == null) continue;
                    Vector d = w.getLocation().toVector().subtract(t.getLocation().toVector());
                    d.setY(0);
                    if (d.lengthSquared() > 4) {
                        d.normalize().multiply(0.45);
                        d.setY(t.getVelocity().getY());
                        t.setVelocity(d);
                    }
                }
            }
            if (age >= 80) {
                for (UUID id : z.who) {
                    Player t = Bukkit.getPlayer(id);
                    if (t != null) Util.effect(t, PotionEffectType.SLOW, 60, 2);
                }
                it.remove();
            }
        }
    }

    private boolean inAnyBubble(Player p) {
        for (Bubble b : bubbles) if (b.members.contains(p.getUniqueId())) return true;
        return false;
    }

    private void tickBubbles() {
        for (Iterator<Bubble> it = bubbles.iterator(); it.hasNext(); ) {
            Bubble b = it.next();
            boolean pop = pl.tick - b.born >= 100;
            List<Player> ps = new ArrayList<>();
            for (UUID m : b.members) {
                Player mp = Bukkit.getPlayer(m);
                if (mp != null && mp.isOnline() && !mp.isDead()) ps.add(mp);
            }
            if (ps.isEmpty()) {
                it.remove();
                continue;
            }
            for (Player mp : ps) if (mp.getLocation().getY() > 200) pop = true;
            if (pop) {
                for (Player mp : ps) {
                    mp.removePotionEffect(PotionEffectType.LEVITATION);
                    Util.particle(mp.getLocation().add(0, 1, 0), "WATER_BUBBLE", 60, 1, 1, 1, 0.2);
                    Util.sound(mp.getLocation(), "BLOCK_GLASS_BREAK", 1.5f, 1.4f);
                }
                it.remove();
                continue;
            }
            for (Player mp : ps) {
                Util.effect(mp, PotionEffectType.LEVITATION, 10, 1);
                Vector vv = mp.getVelocity();
                mp.setVelocity(new Vector(b.dx, vv.getY(), b.dz));
                if (pl.tick % 2 == 0) {
                    Util.particle(mp.getLocation().add(0, 1, 0), "WATER_BUBBLE", 15, 0.7, 0.9, 0.7, 0);
                }
            }
            if (pl.tick % 5 == 0 && b.members.size() < 5) {
                for (Player mp : ps) {
                    for (Player o : Util.playersNear(mp.getLocation(), 3, mp)) {
                        if (b.members.size() >= 5) break;
                        if (b.members.contains(o.getUniqueId()) || inAnyBubble(o)) continue;
                        b.members.add(o.getUniqueId());
                        o.sendMessage(ChatColor.AQUA + "You got swallowed by a bubble!");
                    }
                }
            }
        }
    }

    private void tickMisc() {
        // Rolling coins explode.
        for (Iterator<Map.Entry<UUID, Long>> it = coins.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Long> en = it.next();
            Entity ent = Bukkit.getEntity(en.getKey());
            if (ent == null || ent.isDead()) {
                it.remove();
                continue;
            }
            if (pl.tick >= en.getValue()) {
                Location l = ent.getLocation();
                ent.remove();
                it.remove();
                pl.boom(l, 2.0f);
            }
        }
        // Fake corpses vanish.
        for (Iterator<Map.Entry<UUID, Long>> it = corpses.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Long> en = it.next();
            Entity ent = Bukkit.getEntity(en.getKey());
            if (ent == null || ent.isDead()) {
                it.remove();
                continue;
            }
            if (pl.tick >= en.getValue()) {
                Util.particle(ent.getLocation().add(0, 1, 0), "CLOUD", 20, 0.3, 0.6, 0.3, 0.05);
                ent.remove();
                it.remove();
            }
        }
        // Unclaimed decoy loot vanishes.
        for (Iterator<Map.Entry<UUID, Long>> it = decoyEnd.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Long> en = it.next();
            Entity ent = Bukkit.getEntity(en.getKey());
            if (ent == null || ent.isDead()) {
                decoyOwner.remove(en.getKey());
                it.remove();
                continue;
            }
            if (pl.tick >= en.getValue()) {
                ent.remove();
                decoyOwner.remove(en.getKey());
                it.remove();
            }
        }
    }

    /** Umbrella in the off-hand: slow, floaty fall. */
    private void tickUmbrella() {
        if (pl.tick % 2 != 0) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            Location now = p.getLocation();
            Location prev = lastPos.put(p.getUniqueId(), now);
            if (!items.isUmbrella(p) || prev == null || p.isOnGround()) continue;
            if (!prev.getWorld().equals(now.getWorld())) continue;
            if (now.getY() < prev.getY() - 0.12) {
                p.setVelocity(new Vector((now.getX() - prev.getX()) / 2.0, -0.1, (now.getZ() - prev.getZ()) / 2.0));
                p.setFallDistance(0f);
            }
        }
    }

    // ================================================================== right click

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        String id = items.idOf(e.getItem());
        if (id == null) return;

        Action ac = e.getAction();
        boolean right = ac == Action.RIGHT_CLICK_AIR || ac == Action.RIGHT_CLICK_BLOCK;
        boolean left = ac == Action.LEFT_CLICK_AIR || ac == Action.LEFT_CLICK_BLOCK;

        if (left && id.equals("rubber_duck_blade")) {
            Util.sound(p.getLocation(), "ENTITY_BAT_HURT", 0.7f, 2f);
            return;
        }
        if (!right) return;
        // These keep their normal right-click behaviour (draw, cast, block).
        if (id.equals("longshot_bow") || id.equals("grappling_hook") || id.equals("umbrella")) return;

        // Stop vanilla actions: placing the anvil, eating the baguette, planting the flower...
        e.setCancelled(true);
        if (!p.hasPermission("weapons.use")) return;

        switch (id) {
            case "anvil_hammer":
                if (pl.use(p, id)) anvilHammer(p);
                break;
            case "chicken_cannon":
                if (pl.use(p, id)) cannon(p);
                break;
            case "medics_wand":
                if (pl.use(p, id)) medic(p);
                break;
            case "zoomies_whistle":
                if (pl.use(p, id)) whistle(p);
                break;
            case "bubble_wand":
                if (pl.use(p, id)) bubble(p);
                break;
            case "lucky_coin":
                if (pl.use(p, id)) coin(p);
                break;
            case "fake_death_totem":
                if (pl.use(p, id)) fakeDeath(p);
                break;
            default:
                break;
        }
    }

    // ------------------------------------------------------------------ Anvil Hammer

    private void anvilHammer(Player p) {
        Player t = Util.aimedPlayer(p, 60);
        Location c;
        if (t != null) {
            c = t.getLocation();
        } else {
            Block b = p.getTargetBlock((Set<Material>) null, 60);
            c = b.getLocation().add(0.5, 1, 0.5);
        }
        if (pl.isProtected(c)) {
            p.sendMessage(ChatColor.RED + "That area is protected.");
            return;
        }
        final Location ctr = c.clone();
        final World w = ctr.getWorld();
        final double y = Math.min(ctr.getY() + 100, 250);

        Warn warn = new Warn();
        warn.c = ctr.clone();
        warn.end = pl.tick + 100;
        warns.add(warn);

        Util.sound(p.getLocation(), "ENTITY_WITHER_SPAWN", 0.6f, 1.6f);
        p.sendMessage(ChatColor.GOLD + "Anvils incoming...");

        // One row of 10 per tick so 100 falling blocks don't spawn in the same instant.
        for (int row = 0; row < 10; row++) {
            final int rr = row;
            Bukkit.getScheduler().runTaskLater(pl, () -> {
                for (int col = 0; col < 10; col++) {
                    Location sl = new Location(w, ctr.getX() - 5 + rr + 0.5, y, ctr.getZ() - 5 + col + 0.5);
                    FallingBlock fb = w.spawnFallingBlock(sl, Material.ANVIL, (byte) 0);
                    fb.setHurtEntities(true);
                    fb.setDropItem(false);
                    anvils.add(fb.getUniqueId());
                }
            }, rr);
        }
    }

    @EventHandler
    public void onAnvilLand(EntityChangeBlockEvent e) {
        if (!(e.getEntity() instanceof FallingBlock)) return;
        if (!anvils.remove(e.getEntity().getUniqueId())) return;
        final Block b = e.getBlock();
        boolean keep = pl.getConfig().getBoolean("explosions.block-damage", true) && !pl.isProtected(b.getLocation());
        if (!keep) {
            e.setCancelled(true);
            e.getEntity().remove();
            return;
        }
        // Lets the anvil land as a real block, then clears it a few seconds later.
        placedAnvils.add(b);
        Util.sound(b.getLocation(), "BLOCK_ANVIL_LAND", 1f, 0.8f);
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (b.getType() == Material.ANVIL) b.setType(Material.AIR);
            placedAnvils.remove(b);
        }, 60L);
    }

    // ------------------------------------------------------------------ Chicken Cannon

    private void cannon(Player p) {
        int n = shots.merge(p.getUniqueId(), 1, Integer::sum);
        boolean mega = n % 10 == 0;
        int count = mega ? 1 : 3 + rnd.nextInt(3);
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection();
        for (int i = 0; i < count; i++) {
            Vector spread = new Vector(rnd.nextDouble() - 0.5, rnd.nextDouble() - 0.5, rnd.nextDouble() - 0.5)
                    .multiply(mega ? 0 : 0.25);
            Vector v = dir.clone().add(spread).normalize().multiply(mega ? 1.4 : 1.9);
            spawnChicken(p.getUniqueId(), eye.clone().add(dir.clone().multiply(1.3)), v, mega);
        }
        Util.sound(p.getLocation(), mega ? "ENTITY_ENDERDRAGON_GROWL" : "ENTITY_CHICKEN_AMBIENT", 1.5f, mega ? 1.5f : 0.7f);
        if (mega) Bukkit.broadcastMessage(ChatColor.RED + p.getName() + " fired the GIANT CHICKEN!");
    }

    private void spawnChicken(UUID owner, Location loc, Vector vel, boolean mega) {
        Chicken c = (Chicken) loc.getWorld().spawnEntity(loc, EntityType.CHICKEN);
        c.setCustomName(mega ? ChatColor.RED + "GIANT CHICKEN" : ChatColor.YELLOW + "Cannon Chicken");
        c.setCustomNameVisible(true);
        c.setVelocity(vel);
        Shot s = new Shot();
        s.owner = owner;
        s.mega = mega;
        s.born = pl.tick;
        chickens.put(c.getUniqueId(), s);
    }

    private void explode(Entity c, Shot s) {
        Location l = c.getLocation();
        c.remove();
        Util.sound(l, "ENTITY_CHICKEN_HURT", 1.5f, 0.8f);
        if (s.mega) Util.sound(l, "ENTITY_ENDERDRAGON_GROWL", 1.5f, 1.2f);
        Util.particle(l, "EXPLOSION_LARGE", 1, 0, 0, 0, 0);
        Util.particle(l, "CLOUD", 40, 1, 1, 1, 0.1); // the feathers
        pl.boom(l, s.mega ? 4f : 1.6f);
        Player owner = Bukkit.getPlayer(s.owner);
        for (Entity n : l.getWorld().getNearbyEntities(l, 3, 3, 3)) {
            if (!(n instanceof LivingEntity) || n instanceof Chicken || n instanceof ArmorStand) continue;
            LivingEntity le = (LivingEntity) n;
            Util.dmg(le, s.mega ? 12 : 4, owner);
            if (le instanceof Player && le.isDead() && !le.getUniqueId().equals(s.owner)) {
                pendingChains.add(new Object[]{le.getLocation().add(0, 1, 0), s.owner, le.getUniqueId()});
            }
        }
    }

    // ------------------------------------------------------------------ Medic's Wand

    private void medic(Player p) {
        int r = rnd.nextInt(100);
        Location l = p.getLocation();
        if (r >= 95) { // malpractice
            l.getWorld().strikeLightningEffect(l);
            p.setHealth(Math.min(2.0, p.getMaxHealth()));
            p.sendMessage(ChatColor.RED + "Malpractice!");
            return;
        }
        if (r >= 85) { // wrong patient
            Player t = Util.nearestPlayer(l, 30, p);
            if (t != null) {
                t.setHealth(t.getMaxHealth());
                Util.particle(t.getLocation().add(0, 1, 0), "HEART", 15, 0.5, 0.8, 0.5, 0);
                Util.sound(l, "ENTITY_VILLAGER_NO", 1.5f, 0.6f);
                p.sendMessage(ChatColor.RED + "Oops. You healed " + t.getName() + " instead.");
                return;
            }
        }
        p.setHealth(p.getMaxHealth());
        Util.particle(l.clone().add(0, 1, 0), "HEART", 12, 0.5, 0.8, 0.5, 0);
        Util.sound(l, "ENTITY_PLAYER_LEVELUP", 1f, 1.4f);
        if (r < 21) {
            Util.bar(p, ChatColor.GREEN + "Full heal.");
        } else if (r < 42) {
            p.setVelocity(new Vector(0, 2.0, 0));
            Util.firework(l, Color.LIME);
            Util.bar(p, ChatColor.GREEN + "Full heal, with a rocket!");
        } else if (r < 63) {
            Player t = Util.nearestPlayer(l, 40, p);
            if (t != null) {
                Location tl = t.getLocation();
                t.teleport(l);
                p.teleport(tl);
                Util.bar(p, ChatColor.GREEN + "Full heal, and you swapped with " + t.getName() + "!");
            }
        } else {
            Util.effect(p, PotionEffectType.GLOWING, 600, 0);
            Util.bar(p, ChatColor.GREEN + "Full heal. You're glowing.");
        }
    }

    // ------------------------------------------------------------------ Whistle of Zoomies

    private void whistle(Player p) {
        Zoom z = new Zoom();
        z.whistler = p.getUniqueId();
        z.born = pl.tick;
        List<Player> affected = Util.playersNear(p.getLocation(), 15, p);
        affected.add(p);
        for (Player t : affected) {
            Util.effect(t, PotionEffectType.SPEED, 80, 4);
            z.who.add(t.getUniqueId());
        }
        zooms.add(z);
        Util.sound(p.getLocation(), "ENTITY_BAT_TAKEOFF", 2f, 0.6f);
        Util.sound(p.getLocation(), "ENTITY_BAT_TAKEOFF", 2f, 0.9f);
    }

    // ------------------------------------------------------------------ Bubble Wand

    private void bubble(Player p) {
        Player t = Util.aimedPlayer(p, 30);
        if (t == null) t = p;
        Bubble b = new Bubble();
        b.members.add(t.getUniqueId());
        b.born = pl.tick;
        b.dx = (rnd.nextDouble() - 0.5) * 0.12;
        b.dz = (rnd.nextDouble() - 0.5) * 0.12;
        bubbles.add(b);
        Util.sound(t.getLocation(), "ENTITY_SLIME_SQUISH", 1.5f, 1.5f);
        t.sendMessage(ChatColor.AQUA + "You're in a bubble!");
    }

    // ------------------------------------------------------------------ Lucky Coin

    private void pack(Player t) {
        Util.effect(t, PotionEffectType.INCREASE_DAMAGE, 200, 1);
        Util.effect(t, PotionEffectType.SPEED, 200, 1);
        Util.effect(t, PotionEffectType.DAMAGE_RESISTANCE, 200, 1);
        Util.particle(t.getLocation().add(0, 1, 0), "VILLAGER_HAPPY", 20, 0.5, 0.8, 0.5, 0);
    }

    private void coin(Player p) {
        int r = rnd.nextInt(100);
        Location l = p.getLocation();
        Util.sound(l, "ENTITY_EXPERIENCE_ORB_PICKUP", 1.5f, 1.8f);
        if (r == 0) {
            l.getWorld().strikeLightningEffect(l);
            for (Player o : Bukkit.getOnlinePlayers()) pack(o);
            Bukkit.broadcastMessage(ChatColor.GOLD + p.getName() + "'s coin landed on its EDGE! Everyone gets a buff!");
        } else if (r < 15) {
            Item it = l.getWorld().dropItem(l.clone().add(0, 1, 0), new ItemStack(Material.GOLD_NUGGET));
            it.setPickupDelay(32767);
            it.setVelocity(l.getDirection().multiply(0.5).setY(0.3));
            coins.put(it.getUniqueId(), pl.tick + 60);
            p.sendMessage(ChatColor.YELLOW + "The coin rolls away... and it's ticking.");
        } else if (r < 58) {
            pack(p);
            p.sendMessage(ChatColor.GOLD + "Heads! Buffed for 10 seconds.");
        } else {
            Player t = Util.nearestPlayer(l, 60, p);
            if (t == null) t = p;
            pack(t);
            p.sendMessage(ChatColor.RED + "Tails! " + t.getName() + " got the buff.");
            if (t != p) t.sendMessage(ChatColor.GREEN + p.getName() + "'s coin landed tails. You got a buff!");
        }
    }

    // ------------------------------------------------------------------ Fake Death Totem

    private void fakeDeath(Player p) {
        Location l = p.getLocation();
        Player killer = Util.nearestPlayer(l, 60, p);
        String msg = ChatColor.WHITE + p.getName() + " was slain by " + (killer != null ? killer.getName() : "Zombie");
        for (Player o : Util.playersNear(l, 50, null)) o.sendMessage(msg);

        ArmorStand as = (ArmorStand) l.getWorld().spawnEntity(l, EntityType.ARMOR_STAND);
        as.setCustomName(p.getName());
        as.setCustomNameVisible(true);
        as.setBasePlate(false);
        as.setArms(true);
        ItemStack head = new ItemStack(Material.SKULL_ITEM, 1, (short) 3);
        SkullMeta sm = (SkullMeta) head.getItemMeta();
        sm.setOwner(p.getName());
        head.setItemMeta(sm);
        as.setHelmet(head);
        corpses.put(as.getUniqueId(), pl.tick + 200);

        l.getWorld().strikeLightningEffect(l);
        Util.effect(p, PotionEffectType.INVISIBILITY, 100, 0);

        Material[] bait = {Material.DIAMOND, Material.GOLDEN_APPLE, Material.EMERALD};
        String[] names = {"Diamond", "Golden Apple", "Emerald"};
        for (int i = 0; i < 3; i++) {
            ItemStack is = new ItemStack(bait[i]);
            ItemMeta m = is.getItemMeta();
            m.setDisplayName(ChatColor.AQUA + names[i]);
            is.setItemMeta(m);
            Item it = l.getWorld().dropItem(l.clone().add(0, 1, 0), is);
            it.setVelocity(new Vector(rnd.nextDouble() - 0.5, 0.3, rnd.nextDouble() - 0.5).multiply(0.5));
            decoyOwner.put(it.getUniqueId(), p.getUniqueId());
            decoyEnd.put(it.getUniqueId(), pl.tick + 200);
        }
    }

    @EventHandler
    public void onPickup(PlayerPickupItemEvent e) {
        UUID id = e.getItem().getUniqueId();
        UUID owner = decoyOwner.get(id);
        if (owner == null) return;
        e.setCancelled(true);
        Location l = e.getItem().getLocation();
        e.getItem().remove();
        decoyOwner.remove(id);
        decoyEnd.remove(id);
        if (e.getPlayer().getUniqueId().equals(owner)) return; // the owner just wastes their own bait
        Util.firework(l, Color.PURPLE);
        pl.boom(l, 2.0f);
        e.getPlayer().setVelocity(new Vector(rnd.nextDouble() - 0.5, 0.8, rnd.nextDouble() - 0.5));
        e.getPlayer().sendMessage(ChatColor.RED + "It was a decoy!");
    }

    // ------------------------------------------------------------------ Grappling Hook

    @EventHandler
    public void onFish(PlayerFishEvent e) {
        Player p = e.getPlayer();
        if (!"grappling_hook".equals(items.idOf(p.getInventory().getItemInMainHand()))) return;
        if (!p.hasPermission("weapons.use")) return;
        switch (e.getState()) {
            case IN_GROUND: {
                if (!pl.use(p, "grappling_hook")) return;
                pull(p, e.getHook().getLocation(), p);
                e.getHook().remove();
                break;
            }
            case CAUGHT_ENTITY: {
                Entity c = e.getCaught();
                if (c instanceof Player && pl.use(p, "grappling_hook")) {
                    pull(c, p.getLocation(), p);
                }
                break;
            }
            default:
                break;
        }
    }

    private void pull(Entity who, Location to, Player owner) {
        Vector d = to.toVector().subtract(who.getLocation().toVector());
        double dist = d.length();
        if (dist < 1.5) return;
        if (dist > 30) {
            who.setVelocity(d.normalize().multiply(-2.0).setY(0.8));
            Util.bar(owner, ChatColor.RED + "TWANG! The line snapped.");
            Util.sound(owner.getLocation(), "ENTITY_ZOMBIE_ATTACK_IRON_DOOR", 1f, 1.8f);
            return;
        }
        double speed = Math.min(0.6 + dist * 0.1, 4.0);
        Vector v = d.normalize().multiply(speed);
        v.setY(v.getY() + 0.4);
        if (rnd.nextInt(10) == 0) {
            v.multiply(-1);
            Util.bar(owner, ChatColor.RED + "Reverse grapple!");
        }
        who.setVelocity(v);
        Util.sound(owner.getLocation(), "ENTITY_ARROW_SHOOT", 1f, 1.6f);
    }

    // ================================================================== misc

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        lastPos.remove(e.getPlayer().getUniqueId());
    }

    /** Called on plugin disable: removes everything the items spawned. */
    public void cleanup() {
        for (UUID id : new ArrayList<>(anvils)) {
            Entity en = Bukkit.getEntity(id);
            if (en != null) en.remove();
        }
        anvils.clear();
        for (Block b : new ArrayList<>(placedAnvils)) {
            if (b.getType() == Material.ANVIL) b.setType(Material.AIR);
        }
        placedAnvils.clear();
        for (UUID id : new ArrayList<>(chickens.keySet())) {
            Entity en = Bukkit.getEntity(id);
            if (en != null) en.remove();
        }
        chickens.clear();
        for (UUID id : new ArrayList<>(coins.keySet())) {
            Entity en = Bukkit.getEntity(id);
            if (en != null) en.remove();
        }
        coins.clear();
        for (UUID id : new ArrayList<>(corpses.keySet())) {
            Entity en = Bukkit.getEntity(id);
            if (en != null) en.remove();
        }
        corpses.clear();
        for (UUID id : new ArrayList<>(decoyOwner.keySet())) {
            Entity en = Bukkit.getEntity(id);
            if (en != null) en.remove();
        }
        decoyOwner.clear();
        decoyEnd.clear();
        for (Bubble b : bubbles) {
            for (UUID m : b.members) {
                Player mp = Bukkit.getPlayer(m);
                if (mp != null) mp.removePotionEffect(PotionEffectType.LEVITATION);
            }
        }
        bubbles.clear();
        zooms.clear();
    }
}
