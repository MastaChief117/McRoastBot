// APPROXIMATE stub for offline syntax/type checking ONLY.
package org.bukkit.inventory;

public interface PlayerInventory extends Inventory {
    ItemStack[] getArmorContents();

    ItemStack getItemInMainHand();
}
