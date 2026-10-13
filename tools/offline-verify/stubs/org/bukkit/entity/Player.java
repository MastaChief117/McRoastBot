// APPROXIMATE stub for offline syntax/type checking ONLY.
package org.bukkit.entity;

import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.PlayerInventory;

public interface Player extends HumanEntity, CommandSender {
    UUID getUniqueId();

    World getWorld();

    Location getLocation();

    PlayerInventory getInventory();

    boolean isOp();
}
