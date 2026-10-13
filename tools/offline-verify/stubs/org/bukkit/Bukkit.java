// APPROXIMATE stub for offline syntax/type checking ONLY.
package org.bukkit;

import java.util.Collection;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;

public final class Bukkit {
    private Bukkit() {
    }

    public static int broadcast(Component message) {
        return 0;
    }

    public static Collection<? extends Player> getOnlinePlayers() {
        return null;
    }

    public static BukkitScheduler getScheduler() {
        return null;
    }
}
