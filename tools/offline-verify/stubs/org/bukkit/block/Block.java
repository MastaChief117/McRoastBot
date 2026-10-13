// APPROXIMATE stub for offline syntax/type checking ONLY.
package org.bukkit.block;

import org.bukkit.Material;

public interface Block {
    Material getType();

    Biome getBiome();
}
