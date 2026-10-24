package com.chaos.weapons;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;
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

/** Everything that happens when you hit something (or get hit). All state is in memory only. */
public class CombatListener implements Listener {

    private static final PotionEffectType[] BAD = {
            PotionEffectType.SLOW, PotionEffectType.SLOW_DIGGING, PotionEffectType.CONFUSION,
            PotionEffectType.BLINDNESS, PotionEffectType.HUNGER, PotionEffectType.WEAKNESS,
            PotionEffectType.POISON, PotionEffectType.WITHER, PotionEffectType.LEVITATION,
            PotionEffectType.UNLUCK, PotionEffectType.HARM
    };
    private static final PotionEffectType[] GOOD = {
            PotionEffectType.SPEED, PotionEffectType.FAST_DIGGING, PotionEffectType.INCREASE_DAMAGE,
            PotionEffectType.JUMP, PotionEffectType.REGENERATION, PotionEffectType.DAMAGE_RESISTANCE,
            PotionEffectType.FIRE_RESISTANCE, PotionEffectType.INVISIBILITY, PotionEffectType.NIGHT_VISION,
            PotionEffectType.ABSORPTION, PotionEffectType.SATURATION, PotionEffectType.LUCK,
            PotionEffectType.HEAL
    };

    private static class Duck {
        final UUID ent, target, owner;
        final long born;

        Duck(UUID ent, UUID target, UUID owner, long born) {
            this.ent = ent;
            this.target = target;
            this.owner = owner;
            this.born = born;
        }
    }

    private final ChaosWeapons pl;
    private final Items items;
    private final Random rnd = new Random();

    private final Map<UUID, Integer> combo = new HashMap<>();
    private final Map<UUID, Long> comboLast = new HashMap<>();
    private final Map<UUID, Long> comboCd = new HashMap<>();
    private final Map<UUID, Integer> bagHits = new HashMap<>();
    private final Map<UUID, Double> stored = new HashMap<>();
    private final Map<UUID, Long> lastHit = new HashMap<>();
    private final Map<UUID, Integer> bonkLoss = new HashMap<>();
    private final Map<UUID, Double> moveSpeed = new HashMap<>();
    private final Map<UUID, Long> moveTick = new HashMap<>();
    private final Map<UUID, UUID> lagVictim = new HashMap<>();
    private final Map<UUID, Integer> lagStreak = new HashMap<>();
    private final Map<UUID, Integer> mildHits = new HashMap<>();
    private final Map<UUID, Integer> mildStreak = new HashMap<>();
    private final Map<UUID, Long> arrows = new HashMap<>();
    private final Set<UUID> shock = new HashSet<>();
    private final Set<UUID> hpAdjusted = new HashSet<>();
    private final List<Duck> ducks = new ArrayList<>();

    public CombatListener(ChaosWeapons pl) {
        this.pl = pl;
        this.items = pl.items;
        pl.addTicker(this::tick);
    }

    // ================================================================== ticking

    private void tick() {
        long now = System.currentTimeMillis();

        // Combo Sword: 3 seconds without a hit ends the combo and starts the 30 second cooldown.
        for (Iterator<Map.Entry<UUID, Long>> it = comboLast.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Long> en = it.next();
            if (now - en.getValue() > 3000) {
                UUID u = en.getKey();
                it.remove();
                combo.remove(u);
                comboCd.put(u, now + 30000);
                Player p = Bukkit.getPlayer(u);
                if (p != null) {
                    Util.bar(p, ChatColor.RED + "Combo lost! Cooling down for 30s");
                    Util.sound(p.getLocation(), "ENTITY_VILLAGER_NO", 1f, 1f);
                }
            }
        }

        // Longshot arrows: straight line, exponentially faster.
        for (Iterator<Map.Entry<UUID, Long>> it = arrows.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Long> en = it.next();
            Entity ent = Bukkit.getEntity(en.getKey());
            long age = pl.tick - en.getValue();
            if (ent == null || ent.isDead() || (ent.isOnGround() && age > 2)) {
                it.remove();
                continue;
            }
            if (age > 100) {
                ent.remove();
                it.remove();
                continue;
            }
            Vector v = ent.getVelocity();
            if (v.length() < 8.0) v.multiply(1.10);
            ent.setVelocity(v);
            Util.particle(ent.getLocation(), "CRIT", 2, 0, 0, 0, 0);
        }

        // Fall immunity (Yeet Stick launch, Umbrella bounce). Landing ends it; Yeet landings make a shockwave.
        for (Iterator<Map.Entry<UUID, Long>> it = pl.immune.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Long> en = it.next();
            Entity ent = Bukkit.getEntity(en.getKey());
            long age = pl.tick - en.getValue();
            if (ent == null || ent.isDead() || age > 400) {
                it.remove();
                shock.remove(en.getKey());
                continue;
            }
            if (age > 10 && ent.isOnGround()) {
                it.remove();
                if (shock.remove(en.getKey())) shockwave(ent);
            }
        }

        // Rubber ducks.
        if (pl.tick % 4 == 0) {
            for (Iterator<Duck> it = ducks.iterator(); it.hasNext(); ) {
                Duck d = it.next();
                Entity c = Bukkit.getEntity(d.ent);
                if (c == null || c.isDead()) {
                    it.remove();
                    continue;
                }
                Entity t = Bukkit.getEntity(d.target);
                if (pl.tick - d.born > 200 || t == null || t.isDead()) {
                    popDuck(c, d);
                    it.remove();
                    continue;
                }
                Vector dir = t.getLocation().toVector().subtract(c.getLocation().toVector());
                if (dir.length() > 1.6) {
                    c.setVelocity(dir.normalize().multiply(0.4).setY(0.2));
                } else {
                    if (t instanceof LivingEntity) Util.dmg((LivingEntity) t, 0.5, Bukkit.getPlayer(d.owner));
                    t.setVelocity(new Vector(rnd.nextDouble() - 0.5, 0.3, rnd.nextDouble() - 0.5).multiply(0.6));
                    Util.sound(c.getLocation(), "ENTITY_BAT_HURT", 0.8f, 2f);
                }
            }
        }

        // Max health: Bonk Bat losses and the Glass Cannon.
        if (pl.tick % 10 == 0) {
            for (Player p : Bukkit.getOnlinePlayers()) adjustHealth(p);
        }
    }

    private void adjustHealth(Player p) {
        UUID u = p.getUniqueId();
        boolean glass = "glass_cannon".equals(items.idOf(p.getInventory().getItemInMainHand()));
        int loss = bonkLoss.getOrDefault(u, 0);
        double want = glass ? 2.0 : Math.max(2.0, 20.0 - 2.0 * loss);
        if (want == 20.0 && !hpAdjusted.contains(u)) return;
        if (p.getMaxHealth() != want) p.setMaxHealth(want);
        if (p.getHealth() > want) p.setHealth(want);
        if (want == 20.0) hpAdjusted.remove(u);
        else hpAdjusted.add(u);
    }

    // ================================================================== hits

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (pl.suppress) return;

        // Umbrella: reflect projectiles, bounce falling anvils.
        if (e.getEntity() instanceof Player) {
            Player vp = (Player) e.getEntity();
            if (items.isUmbrella(vp)) {
                if (e.getDamager() instanceof Projectile) {
                    e.setCancelled(true);
                    reflect(vp, (Projectile) e.getDamager());
                    return;
                }
                if (e.getDamager() instanceof FallingBlock) {
                    e.setCancelled(true);
                    vp.setVelocity(new Vector(0, 1.95, 0));
                    pl.immune.put(vp.getUniqueId(), pl.tick);
                    Util.sound(vp.getLocation(), "BLOCK_ANVIL_LAND", 1f, 1.5f);
                    Util.bar(vp, ChatColor.AQUA + "BOING!");
                    return;
                }
            }
        }

        // Longshot arrows: damage grows with flight time.
        if (e.getDamager() instanceof Arrow) {
            Long born = arrows.get(e.getDamager().getUniqueId());
            if (born != null) {
                long age = pl.tick - born;
                e.setDamage(Math.min(120.0, 6.0 + age * 0.8));
                Util.sound(e.getEntity().getLocation(), "ENTITY_PLAYER_ATTACK_CRIT", 1f, 0.6f);
                arrows.remove(e.getDamager().getUniqueId());
            }
            return;
        }

        if (!(e.getDamager() instanceof Player) || !(e.getEntity() instanceof LivingEntity)) return;
        if (e.getCause() != DamageCause.ENTITY_ATTACK) return;

        Player a = (Player) e.getDamager();
        LivingEntity v = (LivingEntity) e.getEntity();
        UUID u = a.getUniqueId();
        long now = System.currentTimeMillis();
        Long prev = lastHit.put(u, now);
        long since = prev == null ? Long.MAX_VALUE : now - prev;

        String id = items.idOf(a.getInventory().getItemInMainHand());
        if (id == null || !a.hasPermission("weapons.use")) return;

        switch (id) {
            case "yeet_stick": {
                if (!pl.ready(a, id)) break;
                e.setDamage(1);
                v.setVelocity(new Vector(0, 2.55, 0)); // about 30 blocks of height
                pl.immune.put(v.getUniqueId(), pl.tick);
                shock.add(v.getUniqueId());
                a.setVelocity(new Vector(0, 0.6, 0));
                Util.sound(v.getLocation(), "ENTITY_FIREWORK_LAUNCH", 1.5f, 0.5f);
                break;
            }
            case "baguette": {
                int n = bagHits.merge(u, 1, Integer::sum);
                Util.sound(v.getLocation(), "BLOCK_NOTE_HARP", 2f, 0.5f);
                Util.sound(v.getLocation(), "BLOCK_NOTE_HARP", 2f, 0.7f);
                if (n < 3) {
                    e.setDamage(8);
                } else {
                    e.setDamage(100);
                    bagHits.remove(u);
                    v.setVelocity(a.getLocation().getDirection().multiply(2.5).setY(1.5));
                    ItemStack stale = new ItemStack(Material.BREAD);
                    ItemMeta m = stale.getItemMeta();
                    m.setDisplayName(ChatColor.GRAY + "Stale Baguette");
                    List<String> lore = new ArrayList<>();
                    lore.add(ChatColor.DARK_GRAY + "It does nothing.");
                    m.setLore(lore);
                    stale.setItemMeta(m);
                    a.getInventory().setItemInMainHand(stale);
                    Bukkit.broadcastMessage(ChatColor.GOLD + "ALAS, THE BAGUETTE HAS FALLEN (" + a.getName() + ")");
                    Util.sound(a.getLocation(), "ENTITY_ITEM_BREAK", 2f, 0.6f);
                }
                break;
            }
            case "swap_a_roo": {
                if (!pl.ready(a, id)) break;
                e.setDamage(1);
                swap(a, v);
                break;
            }
            case "lag_spike_sword": {
                if (!pl.ready(a, id)) break;
                e.setDamage(2);
                Util.effect(v, PotionEffectType.SLOW, 40, 7);
                Util.effect(v, PotionEffectType.JUMP, 40, 250);
                Util.particle(v.getLocation().add(0, 1, 0), "CRIT_MAGIC", 25, 0.4, 0.8, 0.4, 0.1);
                Util.sound(v.getLocation(), "BLOCK_NOTE_BASS", 1.5f, 0.5f);
                if (v instanceof Player) {
                    Player vp = (Player) v;
                    vp.sendTitle(ChatColor.RED + "Connection lost", ChatColor.GRAY + "Reconnecting...", 0, 40, 5);
                    vp.sendMessage(ChatColor.RED + "Connection lost... reconnecting");
                }
                UUID last = lagVictim.put(u, v.getUniqueId());
                int streak = v.getUniqueId().equals(last) ? lagStreak.getOrDefault(u, 0) + 1 : 1;
                lagStreak.put(u, streak);
                if (streak >= 3 && v instanceof Player) {
                    Player vp = (Player) v;
                    vp.sendTitle(ChatColor.RED + "Kicked for flying", "", 0, 40, 5);
                    vp.sendMessage(ChatColor.YELLOW + "[Server] You were kicked for flying. (just kidding)");
                    lagStreak.put(u, 0);
                }
                break;
            }
            case "bonk_bat": {
                if (!pl.ready(a, id)) break;
                Util.sound(v.getLocation(), "ENTITY_ZOMBIE_ATTACK_IRON_DOOR", 1.5f, 0.5f);
                Util.sound(v.getLocation(), "BLOCK_NOTE_BASS", 1.5f, 0.5f);
                if (v instanceof Player) {
                    Player vp = (Player) v;
                    bonkLoss.merge(vp.getUniqueId(), 1, Integer::sum);
                    adjustHealth(vp);
                } else {
                    v.setMaxHealth(Math.max(2.0, v.getMaxHealth() - 2.0));
                }
                if (v.getMaxHealth() <= 6.0) {
                    v.setVelocity(a.getLocation().getDirection().setY(0).normalize().multiply(1.3).setY(0.5));
                }
                break;
            }
            case "rubber_duck_blade": {
                if (!pl.ready(a, id)) break;
                Util.sound(v.getLocation(), "ENTITY_BAT_HURT", 1f, 2f);
                spawnDucks(a, v);
                break;
            }
            case "combo_sword": {
                Long cd = comboCd.get(u);
                if (cd != null && now < cd) {
                    e.setDamage(1);
                    Util.bar(a, ChatColor.RED + "Combo cooling down: " + ((cd - now) / 1000 + 1) + "s");
                    break;
                }
                int n = combo.merge(u, 1, Integer::sum);
                comboLast.put(u, now);
                e.setDamage(7 + n);
                Util.bar(a, ChatColor.AQUA + "Combo x" + n);
                Location l = v.getLocation().add(0, 1, 0);
                if (n == 10) {
                    Util.particle(a.getLocation().add(0, 1, 0), "FIREWORKS_SPARK", 40, 0.5, 0.8, 0.5, 0.1);
                    Util.sound(a.getLocation(), "ENTITY_PLAYER_LEVELUP", 1f, 1f);
                } else if (n == 20) {
                    Util.sound(a.getLocation(), "ENTITY_ENDERDRAGON_GROWL", 1f, 1.5f);
                    Util.particle(a.getLocation().add(0, 1, 0), "FLAME", 60, 0.5, 0.8, 0.5, 0.1);
                } else if (n >= 30) {
                    Bukkit.broadcastMessage(ChatColor.AQUA + a.getName() + " landed a 30-hit COMBO FINISHER!");
                    pl.boom(l, 3f);
                    e.setDamage(7 + n + 30);
                    combo.remove(u);
                    comboLast.remove(u);
                }
                break;
            }
            case "glass_cannon": {
                e.setDamage(250);
                Util.sound(v.getLocation(), "BLOCK_GLASS_BREAK", 2f, 0.7f);
                Util.particle(v.getLocation().add(0, 1, 0), "CRIT", 30, 0.4, 0.6, 0.4, 0.3);
                break;
            }
            case "revenge_blade": {
                double s = stored.getOrDefault(u, 0.0);
                stored.put(u, 0.0);
                e.setDamage(6 + s);
                if (s > 0) {
                    Util.sound(v.getLocation(), "ENTITY_GENERIC_EXPLODE", 1f, 1.4f);
                    Util.particle(v.getLocation().add(0, 1, 0), "CRIT_MAGIC", 40, 0.5, 0.8, 0.5, 0.3);
                    a.sendMessage(ChatColor.DARK_RED + "Revenge released: +" + (int) s + " damage");
                }
                break;
            }
            case "pacifists_blade": {
                if (since >= 60000L) {
                    e.setDamage(150);
                    v.getWorld().strikeLightningEffect(v.getLocation());
                    a.sendMessage(ChatColor.WHITE + "Patience rewarded.");
                } else {
                    e.setDamage(1);
                    Util.bar(a, ChatColor.GRAY + "Not yet: " + ((60000L - since) / 1000 + 1) + "s");
                }
                break;
            }
            case "backstab_dagger": {
                Vector toA = a.getLocation().toVector().subtract(v.getLocation().toVector());
                toA.setY(0);
                Vector face = v.getLocation().getDirection();
                face.setY(0);
                boolean behind = toA.lengthSquared() > 0 && face.lengthSquared() > 0
                        && face.normalize().dot(toA.normalize()) < -0.2;
                if (behind) {
                    e.setDamage(60);
                    Util.sound(v.getLocation(), "BLOCK_NOTE_BASS", 2f, 0.5f);
                    Bukkit.getScheduler().runTaskLater(pl, () -> Util.sound(v.getLocation(), "BLOCK_NOTE_BASS", 2f, 0.5f), 5L);
                    Bukkit.getScheduler().runTaskLater(pl, () -> Util.sound(v.getLocation(), "BLOCK_NOTE_BASS", 2f, 0.4f), 10L);
                    a.sendMessage(ChatColor.RED + "Backstab!");
                } else {
                    e.setDamage(0.5);
                    Util.bar(a, ChatColor.GRAY + "Not from the front.");
                    if (v instanceof Player) ((Player) v).sendMessage(ChatColor.GRAY + "You thought.");
                }
                break;
            }
            case "momentum_hammer": {
                double speed = (pl.tick - moveTick.getOrDefault(u, -100L) <= 3) ? moveSpeed.getOrDefault(u, 0.0) : 0.0;
                double dmg = 1.0;
                if (speed > 0.15) dmg += (speed - 0.15) * 150.0;
                dmg += a.getFallDistance() * 4.0;
                dmg = Math.min(dmg, 150.0);
                e.setDamage(dmg);
                if (dmg > 10) {
                    Util.sound(v.getLocation(), "ENTITY_PLAYER_ATTACK_KNOCKBACK", 1.5f, 0.6f);
                    Util.particle(v.getLocation().add(0, 1, 0), "EXPLOSION_NORMAL", 10, 0.3, 0.5, 0.3, 0.1);
                }
                break;
            }
            case "thunder_sword": {
                World w = a.getWorld();
                boolean stormy = w.hasStorm() || w.isThundering()
                        || v.getLocation().getY() > 150 || a.getLocation().getY() > 150;
                if (stormy) {
                    e.setDamage(e.getDamage() + 4);
                    if (pl.getConfig().getBoolean("thunder.real-lightning", true)) w.strikeLightning(v.getLocation());
                    else w.strikeLightningEffect(v.getLocation());
                } else {
                    e.setDamage(2);
                    Util.bar(a, ChatColor.GRAY + "The sky isn't angry enough.");
                }
                break;
            }
            case "mild_inconvenience": {
                int n = mildHits.merge(u, 1, Integer::sum);
                int streak = mildStreak.merge(u, 1, Integer::sum);
                if (streak >= 10) {
                    mildStreak.put(u, 0);
                    Bukkit.broadcastMessage(ChatColor.GREEN + a.getName() + " hit the MILD INCONVENIENCE JACKPOT!");
                    for (PotionEffectType t : BAD) {
                        applyEffect(v, t);
                        applyEffect(a, t);
                    }
                    for (PotionEffectType t : GOOD) {
                        applyEffect(v, t);
                        applyEffect(a, t);
                    }
                    break;
                }
                boolean reverse = rnd.nextInt(20) == 0;
                int rolls = (n % 5 == 0) ? 2 : 1;
                for (int i = 0; i < rolls; i++) {
                    PotionEffectType bad = BAD[rnd.nextInt(BAD.length)];
                    PotionEffectType good = GOOD[rnd.nextInt(GOOD.length)];
                    if (reverse) {
                        applyEffect(a, bad);
                        applyEffect(v, good);
                    } else {
                        applyEffect(v, bad);
                        applyEffect(a, good);
                    }
                }
                if (reverse) Util.bar(a, ChatColor.RED + "Inconvenience Reversal!");
                break;
            }
            default:
                break;
        }
    }

    private void applyEffect(LivingEntity e, PotionEffectType t) {
        Util.effect(e, t, t.isInstant() ? 1 : 20, 0);
    }

    private void swap(Player a, LivingEntity v) {
        Location la = a.getLocation();
        Location lv = v.getLocation();
        List<Player> others = Util.playersNear(la, 10, a);
        others.remove(v);
        a.teleport(lv);
        v.teleport(la);
        Util.sound(la, "ENTITY_ENDERMEN_TELEPORT", 1f, 1.2f);
        Util.sound(lv, "ENTITY_ENDERMEN_TELEPORT", 1f, 0.8f);
        if (others.size() >= 2) {
            List<Location> spots = new ArrayList<>();
            for (Player o : others) spots.add(o.getLocation());
            java.util.Collections.shuffle(spots, rnd);
            for (int i = 0; i < others.size(); i++) others.get(i).teleport(spots.get(i));
        }
    }

    private void spawnDucks(Player a, LivingEntity v) {
        int active = 0;
        for (Duck d : ducks) if (d.owner.equals(a.getUniqueId())) active++;
        for (int i = 0; i < 4 && active < 12; i++, active++) {
            Location l = v.getLocation().add(rnd.nextDouble() * 2 - 1, 0.5, rnd.nextDouble() * 2 - 1);
            Chicken c = (Chicken) v.getWorld().spawnEntity(l, EntityType.CHICKEN);
            c.setBaby();
            c.setCustomName(ChatColor.YELLOW + "Rubber Duck");
            c.setCustomNameVisible(true);
            c.setInvulnerable(true);
            ducks.add(new Duck(c.getUniqueId(), v.getUniqueId(), a.getUniqueId(), pl.tick));
        }
    }

    private void popDuck(Entity c, Duck d) {
        Location l = c.getLocation();
        c.remove();
        Util.particle(l, "FIREWORKS_SPARK", 20, 0.3, 0.3, 0.3, 0.1);
        Util.sound(l, "ENTITY_BAT_HURT", 1.2f, 2f);
        Player owner = Bukkit.getPlayer(d.owner);
        for (Entity n : l.getWorld().getNearbyEntities(l, 3, 3, 3)) {
            if (n instanceof LivingEntity && !(n instanceof Chicken) && !(n instanceof ArmorStand)) {
                Util.dmg((LivingEntity) n, 1.5, owner);
            }
        }
    }

    private void shockwave(Entity ent) {
        Location l = ent.getLocation();
        Util.particle(l, "EXPLOSION_LARGE", 3, 1, 0.2, 1, 0);
        Util.sound(l, "ENTITY_GENERIC_EXPLODE", 1.5f, 0.7f);
        for (Entity n : ent.getNearbyEntities(5, 3, 5)) {
            if (!(n instanceof LivingEntity) || n instanceof ArmorStand) continue;
            Vector v = new Vector(rnd.nextDouble() - 0.5, 0, rnd.nextDouble() - 0.5);
            if (v.lengthSquared() == 0) v = new Vector(1, 0, 0);
            v.normalize().multiply(1.2).setY(0.9 + rnd.nextDouble() * 0.5);
            n.setVelocity(v);
        }
    }

    private void reflect(Player vp, Projectile pr) {
        ProjectileSource src = pr.getShooter();
        pr.remove();
        Util.sound(vp.getLocation(), "ENTITY_ZOMBIE_ATTACK_IRON_DOOR", 1f, 1.5f);
        if (src instanceof LivingEntity && src != vp) {
            LivingEntity sh = (LivingEntity) src;
            Vector dir = sh.getEyeLocation().toVector().subtract(vp.getEyeLocation().toVector()).normalize();
            Arrow n = vp.getWorld().spawnArrow(vp.getEyeLocation().add(dir.clone().multiply(1.5)), dir, 4.5f, 0f);
            n.setShooter(vp);
        }
    }

    // ================================================================== bow

    @EventHandler(ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        if (!"longshot_bow".equals(items.idOf(e.getBow()))) return;
        if (!(e.getProjectile() instanceof Arrow)) return;
        Arrow ar = (Arrow) e.getProjectile();
        ar.setGravity(false);
        ar.setCritical(true);
        arrows.put(ar.getUniqueId(), pl.tick);
        Util.sound(e.getEntity().getLocation(), "ENTITY_ARROW_SHOOT", 1.5f, 0.6f);
    }

    // ================================================================== misc events

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFall(EntityDamageEvent e) {
        if (e.getCause() == DamageCause.FALL && pl.immune.containsKey(e.getEntity().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAnyDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        Player p = (Player) e.getEntity();
        UUID u = p.getUniqueId();
        mildStreak.remove(u);
        if ("revenge_blade".equals(items.idOf(p.getInventory().getItemInMainHand()))) {
            double s = stored.getOrDefault(u, 0.0) + e.getFinalDamage();
            stored.put(u, Math.min(300.0, s));
            Util.bar(p, ChatColor.DARK_RED + "Revenge stored: " + (int) Math.min(300.0, s));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Location f = e.getFrom();
        Location t = e.getTo();
        if (t == null) return;
        UUID u = e.getPlayer().getUniqueId();
        moveSpeed.put(u, Math.hypot(t.getX() - f.getX(), t.getZ() - f.getZ()));
        moveTick.put(u, pl.tick);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        final Player p = e.getPlayer();
        bonkLoss.remove(p.getUniqueId());
        Bukkit.getScheduler().runTask(pl, () -> adjustHealth(p));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        UUID u = p.getUniqueId();
        p.setMaxHealth(20.0);
        hpAdjusted.remove(u);
        bonkLoss.remove(u);
        combo.remove(u);
        comboLast.remove(u);
        comboCd.remove(u);
        bagHits.remove(u);
        stored.remove(u);
        lastHit.remove(u);
        moveSpeed.remove(u);
        moveTick.remove(u);
        lagVictim.remove(u);
        lagStreak.remove(u);
        mildHits.remove(u);
        mildStreak.remove(u);
        pl.immune.remove(u);
        shock.remove(u);
    }

    /** Called on plugin disable: remove ducks and give everyone their normal max health back. */
    public void cleanup() {
        for (Duck d : ducks) {
            Entity c = Bukkit.getEntity(d.ent);
            if (c != null) c.remove();
        }
        ducks.clear();
        for (UUID id : arrows.keySet()) {
            Entity ar = Bukkit.getEntity(id);
            if (ar != null) ar.remove();
        }
        arrows.clear();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (hpAdjusted.contains(p.getUniqueId())) p.setMaxHealth(20.0);
        }
        hpAdjusted.clear();
    }
}
