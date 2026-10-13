// APPROXIMATE stub for offline syntax/type checking ONLY.
package org.bukkit.scheduler;

import org.bukkit.plugin.Plugin;

public interface BukkitScheduler {
    void runTaskLater(Plugin plugin, Runnable task, long delay);

    void runTask(Plugin plugin, Runnable task);

    void runTaskAsynchronously(Plugin plugin, Runnable task);
}
