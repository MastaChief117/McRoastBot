// APPROXIMATE stub for offline syntax/type checking ONLY.
package org.bukkit;

import java.util.Collection;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;

public interface Server {
    PluginManager getPluginManager();

    Collection<? extends Player> getOnlinePlayers();

    BukkitScheduler getScheduler();
}
