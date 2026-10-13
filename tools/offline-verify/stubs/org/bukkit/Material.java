// APPROXIMATE stub for offline syntax/type checking ONLY.
package org.bukkit;

public enum Material {
    TNT,
    OAK_BOAT,
    ARMOR_STAND,
    AIR;

    public boolean isAir() {
        return this == AIR;
    }
}
