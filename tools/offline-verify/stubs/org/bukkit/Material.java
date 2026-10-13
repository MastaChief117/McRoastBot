// APPROXIMATE stub for offline syntax/type checking ONLY.
// Extra constants exist so offline fakes can build armor/tool scenarios.
package org.bukkit;

public enum Material {
    TNT,
    OAK_BOAT,
    ARMOR_STAND,
    LEATHER_CHESTPLATE,
    WOODEN_PICKAXE,
    STONE_PICKAXE,
    DIAMOND_SWORD,
    DIRT,
    AIR;

    public boolean isAir() {
        return this == AIR;
    }
}
