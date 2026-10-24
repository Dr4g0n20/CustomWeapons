package com.chaos.weapons;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

public final class Util {

    private Util() {
    }

    /** Plays a sound by enum name. A wrong name is ignored instead of crashing. */
    public static void sound(Location l, String name, float vol, float pitch) {
        try {
            l.getWorld().playSound(l, Sound.valueOf(name), vol, pitch);
        } catch (Exception ignored) {
        }
    }

    /** Spawns a particle by enum name. A wrong name is ignored instead of crashing. */
    public static void particle(Location l, String name, int count, double dx, double dy, double dz, double speed) {
        try {
            l.getWorld().spawnParticle(Particle.valueOf(name), l, count, dx, dy, dz, speed);
        } catch (Exception ignored) {
        }
    }

    public static void bar(Player p, String msg) {
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(msg));
    }

    public static void effect(LivingEntity e, PotionEffectType t, int ticks, int amp) {
        e.addPotionEffect(new PotionEffect(t, ticks, amp), true);
    }

    /** Damage dealt by an item ability. Our own damage handlers skip it so abilities can't trigger themselves. */
    public static void dmg(LivingEntity v, double amount, Entity source) {
        ChaosWeapons pl = ChaosWeapons.inst;
        boolean old = pl.suppress;
        pl.suppress = true;
        try {
            if (source != null) v.damage(amount, source);
            else v.damage(amount);
        } finally {
            pl.suppress = old;
        }
    }

    /** The player the shooter is looking at (within a narrow cone), or null. */
    public static Player aimedPlayer(Player p, double range) {
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection();
        Player best = null;
        double bestDist = range + 1;
        for (Entity e : p.getNearbyEntities(range, range, range)) {
            if (!(e instanceof Player)) continue;
            Player t = (Player) e;
            if (t.getGameMode() == GameMode.SPECTATOR) continue;
            Vector to = t.getLocation().add(0, 1, 0).toVector().subtract(eye.toVector());
            double dist = to.length();
            if (dist > range || dist < 0.5) continue;
            if (to.normalize().dot(dir) > 0.975 && dist < bestDist) {
                best = t;
                bestDist = dist;
            }
        }
        return best;
    }

    public static List<Player> playersNear(Location l, double r, Player exclude) {
        List<Player> out = new ArrayList<>();
        for (Player o : l.getWorld().getPlayers()) {
            if (o == exclude || o.getGameMode() == GameMode.SPECTATOR) continue;
            if (o.getLocation().distanceSquared(l) <= r * r) out.add(o);
        }
        return out;
    }

    public static Player nearestPlayer(Location l, double r, Player exclude) {
        Player best = null;
        double bestSq = r * r;
        for (Player o : l.getWorld().getPlayers()) {
            if (o == exclude || o.getGameMode() == GameMode.SPECTATOR) continue;
            double d = o.getLocation().distanceSquared(l);
            if (d <= bestSq) {
                best = o;
                bestSq = d;
            }
        }
        return best;
    }

    public static void firework(Location l, Color color) {
        try {
            final Firework fw = (Firework) l.getWorld().spawnEntity(l, EntityType.FIREWORK);
            FireworkMeta m = fw.getFireworkMeta();
            m.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.BALL_LARGE)
                    .withColor(color).withFade(Color.WHITE).flicker(true).build());
            m.setPower(0);
            fw.setFireworkMeta(m);
            Bukkit.getScheduler().runTaskLater(ChaosWeapons.inst, fw::detonate, 2L);
        } catch (Exception ignored) {
        }
    }
}
