// APPROXIMATE stub for offline syntax/type checking ONLY.
package org.bukkit.event.inventory;

import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.Recipe;

public class CraftItemEvent extends InventoryClickEvent {
    public Recipe getRecipe() {
        return null;
    }

    @Override
    public CraftingInventory getInventory() {
        return null;
    }
}
