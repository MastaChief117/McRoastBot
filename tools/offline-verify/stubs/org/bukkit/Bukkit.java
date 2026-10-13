// APPROXIMATE stub for offline syntax/type checking ONLY.
// TEST HOOKS (static mutable state) exist so the offline integration harness
// can drive listeners without a server. The real Bukkit class differs;
// source compatibility of the members the plugin uses is what matters.
package org.bukkit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;

public final class Bukkit {
    private Bukkit() {
    }

    public static BukkitScheduler scheduler;
    public static Collection<? extends Player> online = List.of();
    public static final List<Component> broadcastLog = new ArrayList<>();

    public static int broadcast(Component message) {
        broadcastLog.add(message);
        return 1;
    }

    public static Collection<? extends Player> getOnlinePlayers() {
        return online;
    }

    public static BukkitScheduler getScheduler() {
        return scheduler;
    }
}
