// APPROXIMATE stub for offline syntax/type checking ONLY.
package org.bukkit;

import java.util.Collection;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;

public interface World {
    enum Environment { NORMAL, NETHER, THE_END, CUSTOM }

    Environment getEnvironment();

    Collection<Entity> getNearbyEntities(Location location, double x, double y, double z);

    Block getBlockAt(int x, int y, int z);
}
