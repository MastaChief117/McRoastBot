package com.steelquill.mcroastbot.observe;

import com.steelquill.mcroastbot.core.Observation;
import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.ItemStack;

/**
 * Sync-only world reading. Scans one 6x4x6 area max, counts entities in a
 * small radius, summarizes gear. Must run on the main thread.
 */
public final class ObserveGatherer {
    public enum Param { ARMOR, INVENTORY, ENTITIES, BLOCKS, BIOME, DIMENSION }

    private ObserveGatherer() {
    }

    public static Observation gather(Player p, Param param, Set<String> rareBiomes) {
        Observation ob = new Observation(p.getUniqueId(), p.getName());
        ob.health = p.getHealth();
        try {
            ob.dimension = p.getWorld().getEnvironment().name().toLowerCase(Locale.ROOT);
        } catch (Exception ignored) {
            ob.dimension = "unknown";
        }
        try {
            ob.biome = p.getLocation().getBlock().getBiome().name().toLowerCase(Locale.ROOT);
        } catch (Exception ignored) {
            ob.biome = "unknown";
        }
        switch (param) {
            case ARMOR -> fillArmor(p, ob);
            case INVENTORY -> fillInventory(p, ob);
            case ENTITIES -> fillEntities(p, ob);
            case BLOCKS -> fillBlocks(p, ob);
            case BIOME -> {
                if (rareBiomes.contains(ob.biome)) {
                    ob.flags.add("rare_biome");
                }
            }
            case DIMENSION -> {
                if (ob.dimension.contains("nether") || ob.dimension.contains("end")) {
                    ob.flags.add("dangerous_dimension");
                }
            }
        }
        return ob;
    }

    /** Basic observation used by event detectors (cheap, no scans). */
    public static Observation basic(Player p) {
        Observation ob = new Observation(p.getUniqueId(), p.getName());
        ob.health = p.getHealth();
        try {
            ob.dimension = p.getWorld().getEnvironment().name().toLowerCase(Locale.ROOT);
            ob.biome = p.getLocation().getBlock().getBiome().name().toLowerCase(Locale.ROOT);
        } catch (Exception ignored) {
        }
        fillArmor(p, ob);
        fillInventory(p, ob);
        return ob;
    }

    private static void fillArmor(Player p, Observation ob) {
        try {
            ItemStack[] armor = p.getInventory().getArmorContents();
            StringBuilder sb = new StringBuilder();
            boolean poor = true;
            for (ItemStack s : armor) {
                String m = (s == null) ? "none" : s.getType().name().toLowerCase(Locale.ROOT);
                if (sb.length() > 0) {
                    sb.append('+');
                }
                sb.append(m);
                if (m.contains("iron") || m.contains("diamond") || m.contains("netherite")) {
                    poor = false;
                }
            }
            ob.armor = sb.toString();
            if (poor) {
                ob.flags.add("poor_gear");
            }
        } catch (Exception ignored) {
            ob.armor = "unknown";
        }
    }

    private static void fillInventory(Player p, Observation ob) {
        try {
            ItemStack held = p.getInventory().getItemInMainHand();
            String m = (held == null) ? "empty_hand"
                    : held.getType().name().toLowerCase(Locale.ROOT);
            ob.held = m;
            if (m.contains("wooden") || m.contains("stone") || m.equals("empty_hand")) {
                ob.flags.add("poor_tools");
            }
        } catch (Exception ignored) {
            ob.held = "unknown";
        }
    }

    private static void fillEntities(Player p, Observation ob) {
        Collection<Entity> near;
        try {
            near = p.getWorld().getNearbyEntities(p.getLocation(), 8, 6, 8);
        } catch (Exception ignored) {
            return;
        }
        for (Entity e : near) {
            if (e instanceof Player) {
                bump(ob, "players");
            } else if (e instanceof Boat) {
                bump(ob, "boats");
            } else if (e instanceof ArmorStand) {
                bump(ob, "armor_stands");
            } else if (e instanceof TNTPrimed) {
                bump(ob, "primed_tnt");
            } else if (e instanceof Creeper) {
                bump(ob, "creeper");
            } else if (e instanceof Enderman) {
                bump(ob, "enderman");
            } else if (e instanceof Villager) {
                bump(ob, "villager");
            } else if (e instanceof Zombie) {
                bump(ob, "zombie");
            } else {
                bump(ob, "other_mobs");
            }
        }
        if (ob.nearby.getOrDefault("boats", 0) >= 6) {
            ob.flags.add("large_boat_cluster");
        }
        if (ob.nearby.getOrDefault("armor_stands", 0) >= 6) {
            ob.flags.add("large_armor_stand_cluster");
        }
        if (ob.nearby.getOrDefault("primed_tnt", 0) >= 1) {
            ob.flags.add("live_tnt_nearby");
        }
        if (ob.nearby.getOrDefault("creeper", 0) >= 1) {
            ob.flags.add("creeper_nearby");
        }
        if (ob.nearby.getOrDefault("enderman", 0) >= 1) {
            ob.flags.add("enderman_nearby");
        }
    }

    private static void fillBlocks(Player p, Observation ob) {
        int tnt = 0;
        try {
            World w = p.getWorld();
            int bx = p.getLocation().getBlockX();
            int by = p.getLocation().getBlockY();
            int bz = p.getLocation().getBlockZ();
            // 6x4x6 window around the feet.
            for (int dx = -3; dx < 3; dx++) {
                for (int dy = -2; dy < 2; dy++) {
                    for (int dz = -3; dz < 3; dz++) {
                        Block b = w.getBlockAt(bx + dx, by + dy, bz + dz);
                        if (b.getType() == Material.TNT) {
                            tnt++;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            return;
        }
        if (tnt > 0) {
            ob.nearby.put("tnt_blocks", tnt);
        }
        if (tnt >= 4) {
            ob.flags.add("tnt_stash");
        }
    }

    private static void bump(Observation ob, String key) {
        ob.nearby.put(key, ob.nearby.getOrDefault(key, 0) + 1);
    }
}
