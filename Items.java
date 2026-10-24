package com.chaos.weapons;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Items {

    public enum Cat {
        CORE(ChatColor.GOLD, "CORE"),
        CONDITIONAL(ChatColor.AQUA, "CONDITIONAL"),
        SUPPORT(ChatColor.GREEN, "SUPPORT");

        public final ChatColor color;
        public final String label;

        Cat(ChatColor color, String label) {
            this.color = color;
            this.label = label;
        }
    }

    public static final class Def {
        public final String id, name, teaser, info;
        public final Material mat;
        public final Cat cat;

        Def(String id, String name, Material mat, Cat cat, String teaser, String info) {
            this.id = id;
            this.name = name;
            this.mat = mat;
            this.cat = cat;
            this.teaser = teaser;
            this.info = info;
        }
    }

    private static final String TAG = "cw:";

    private final ChaosWeapons pl;
    private final Map<String, Def> defs = new LinkedHashMap<>();

    public Items(ChaosWeapons pl) {
        this.pl = pl;

        // ---------------- CORE ----------------
        reg("anvil_hammer", "Anvil Hammer", Material.ANVIL, Cat.CORE, "Right-click. Look up. Run.",
                "Right-click a player you're aiming at (or the block you look at). A 10x10 field of anvils appears 100 blocks up and falls a few seconds later.");
        reg("chicken_cannon", "Chicken Cannon", Material.RAW_CHICKEN, Cat.CORE, "Cluck. Boom.",
                "Right-click to fire 3-5 chickens that explode on impact. Every 10th shot is a GIANT chicken. A chicken that kills spawns another that hunts the next player.");
        reg("yeet_stick", "Yeet Stick", Material.STICK, Cat.CORE, "Sends people places.",
                "Hit a player to launch them 30 blocks up. When they land, a shockwave yeets everyone nearby. You hop a little too.");
        reg("baguette", "Baguette", Material.BREAD, Cat.CORE, "Fresh. Won't last.",
                "Hits 1 and 2 hurt. Hit 3 deals 100 damage and launches them. Then the baguette breaks.");
        reg("swap_a_roo", "Swap-a-roo", Material.SLIME_BALL, Cat.CORE, "Nobody knows where they are.",
                "Hit a player to swap places with them. Everyone else within 10 blocks gets shuffled around.");
        reg("lag_spike_sword", "Lag Spike Sword", Material.WATCH, Cat.CORE, "Please wait...",
                "Hit a player to freeze them for 2 seconds with fake connection errors. Three hits in a row on one player fake-kicks them.");
        reg("bonk_bat", "Bonk Bat", Material.BONE, Cat.CORE, "Bonk.",
                "Each hit bonks and removes 1 heart of max health until they respawn. At 3 hearts or less they get launched too.");
        reg("rubber_duck_blade", "Rubber Duck Blade", Material.GOLD_SWORD, Cat.CORE, "Squeak.",
                "Squeaks on every swing. Hits spawn rubber ducks that mob the target, then pop after 10 seconds.");

        // ---------------- CONDITIONAL ----------------
        reg("combo_sword", "Combo Sword", Material.DIAMOND_SWORD, Cat.CONDITIONAL, "Don't stop swinging.",
                "Each hit adds +1 damage. 10, 20 and 30 hits trigger effects. Stop hitting for 3 seconds and it resets, then cools down for 30 seconds.");
        reg("longshot_bow", "Longshot Bow", Material.BOW, Cat.CONDITIONAL, "Faster the longer it flies.",
                "Arrows fly in a straight line and get exponentially faster. The longer they fly, the harder they hit. Comes with Infinity.");
        reg("glass_cannon", "Glass Cannon", Material.GLASS_BOTTLE, Cat.CONDITIONAL, "Hits hard. Breaks easy.",
                "Massive damage, but your max health is 1 heart while it's in your main hand.");
        reg("revenge_blade", "Revenge Blade", Material.STONE_SWORD, Cat.CONDITIONAL, "Remembers every hit.",
                "Stores the damage you take while holding it. Your next hit releases it all.");
        reg("pacifists_blade", "Pacifist's Blade", Material.RED_ROSE, Cat.CONDITIONAL, "Patience is a weapon.",
                "Huge damage, but only if you haven't hit anyone for 60 seconds. Any hit resets the timer.");
        reg("backstab_dagger", "Backstab Dagger", Material.FLINT, Cat.CONDITIONAL, "Not from the front.",
                "Huge damage from behind. Almost nothing from the front.");
        reg("momentum_hammer", "Momentum Hammer", Material.IRON_AXE, Cat.CONDITIONAL, "Bring speed.",
                "Damage scales with your speed and fall distance. Standing still does almost nothing.");
        reg("thunder_sword", "Thunder Sword", Material.IRON_SWORD, Cat.CONDITIONAL, "Likes bad weather.",
                "In rain, thunder, or above y=150, every hit calls lightning on the target. Otherwise it's weak.");

        // ---------------- SUPPORT ----------------
        reg("mild_inconvenience", "Sword of Mild Inconvenience", Material.WOOD_SWORD, Cat.SUPPORT, "Everyone gets something.",
                "Each hit gives the enemy a random bad effect and you a random good one (level 1, 1 second). Every 5th hit rolls twice. 10 hits in a row without being hit = Jackpot.");
        reg("medics_wand", "Medic's Wand", Material.BLAZE_ROD, Cat.SUPPORT, "Results may vary.",
                "Right-click to heal yourself with a random twist: launch, swap, glow... 10% heals the nearest enemy instead, 5% is malpractice.");
        reg("zoomies_whistle", "Whistle of Zoomies", Material.FEATHER, Cat.SUPPORT, "Everybody run.",
                "Right-click: everyone within 15 blocks gets Speed V and is dragged toward you. Then everyone is slowed.");
        reg("bubble_wand", "Bubble Wand", Material.PRISMARINE_CRYSTALS, Cat.SUPPORT, "Don't pop it.",
                "Right-click a player to trap them in a floating bubble that drifts and swallows anyone it bumps into (max 5). It pops after 5 seconds.");
        reg("grappling_hook", "Grappling Hook", Material.FISHING_ROD, Cat.SUPPORT, "Aim carefully.",
                "Cast to pull yourself to where the hook lands. Hook a player and reel in to pull them to you. 10% reverse grapple. Lines over 30 blocks snap.");
        reg("umbrella", "Umbrella", Material.SHIELD, Cat.SUPPORT, "Not just for rain.",
                "Hold in your off-hand. Reflects projectiles back at the shooter, bounces anvils (and launches you), and slows your fall.");
        reg("lucky_coin", "Lucky Coin", Material.GOLD_NUGGET, Cat.SUPPORT, "Heads or tails?",
                "Right-click to flip. Heads buffs you, tails buffs the nearest player, edge (1%) buffs everyone, and sometimes it rolls away and explodes.");
        reg("fake_death_totem", "Fake Death Totem", Material.TOTEM, Cat.SUPPORT, "Playing dead, loudly.",
                "Right-click to fake your death: fake chat message, fake corpse, 5 seconds of invisibility, and decoy loot that explodes when picked up.");
    }

    private void reg(String id, String name, Material mat, Cat cat, String teaser, String info) {
        defs.put(id, new Def(id, name, mat, cat, teaser, info));
    }

    public Def get(String id) {
        return defs.get(id);
    }

    public Collection<Def> all() {
        return defs.values();
    }

    private Material material(Def d) {
        String o = pl.getConfig().getString("materials." + d.id);
        if (o != null) {
            Material m = Material.matchMaterial(o);
            if (m != null) return m;
        }
        return d.mat;
    }

    /** Writes text as invisible colour-code pairs so it never shows in the tooltip. */
    private static String hide(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) sb.append('\u00a7').append(c);
        return sb.toString();
    }

    public ItemStack create(String id) {
        Def d = defs.get(id);
        if (d == null) return null;
        ItemStack it = new ItemStack(material(d));
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(d.cat.color + "" + ChatColor.BOLD + d.name);
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "" + ChatColor.ITALIC + d.teaser);
        lore.add(d.cat.color + d.cat.label);
        lore.add(hide(TAG + id)); // hidden ID, always the last line
        m.setLore(lore);
        m.spigot().setUnbreakable(true);
        m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ENCHANTS);
        if (id.equals("longshot_bow")) m.addEnchant(Enchantment.ARROW_INFINITE, 1, true);
        it.setItemMeta(m);
        return it;
    }

    /** Returns the custom item ID of a stack, or null if it isn't one of ours. */
    public String idOf(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        ItemMeta m = it.getItemMeta();
        if (!m.hasLore()) return null;
        List<String> lore = m.getLore();
        if (lore.isEmpty()) return null;
        String plain = lore.get(lore.size() - 1).replace("\u00a7", "");
        if (!plain.startsWith(TAG)) return null;
        String id = plain.substring(TAG.length());
        return defs.containsKey(id) ? id : null;
    }

    public boolean isUmbrella(Player p) {
        return "umbrella".equals(idOf(p.getInventory().getItemInOffHand()));
    }

    public void give(Player p, String id) {
        List<ItemStack> stacks = new ArrayList<>();
        stacks.add(create(id));
        if (id.equals("longshot_bow")) stacks.add(new ItemStack(Material.ARROW, 1)); // Infinity needs one arrow
        for (ItemStack s : stacks) {
            for (ItemStack left : p.getInventory().addItem(s).values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), left);
            }
        }
    }

    /** The Weapon Guide: a written book with one page per item. Hidden until opened. */
    public ItemStack guide() {
        ItemStack b = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta m = (BookMeta) b.getItemMeta();
        m.setTitle("Weapon Guide");
        m.setAuthor("ChaosWeapons");
        for (Def d : defs.values()) {
            m.addPage(d.cat.color + "" + ChatColor.BOLD + d.name + "\n"
                    + ChatColor.DARK_GRAY + d.cat.label + "\n\n"
                    + ChatColor.BLACK + d.info);
        }
        b.setItemMeta(m);
        return b;
    }
}
