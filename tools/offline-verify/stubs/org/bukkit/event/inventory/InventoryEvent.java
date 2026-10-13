// APPROXIMATE stub for offline syntax/type checking ONLY.
package org.bukkit.event.inventory;

import org.bukkit.entity.HumanEntity;
import org.bukkit.event.Event;
import org.bukkit.inventory.Inventory;

public abstract class InventoryEvent extends Event {
    public Inventory getInventory() {
        return null;
    }

    public HumanEntity getWhoClicked() {
        return null;
    }
}
