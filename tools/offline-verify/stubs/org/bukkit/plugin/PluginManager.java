// APPROXIMATE stub for offline syntax/type checking ONLY.
package org.bukkit.plugin;

import org.bukkit.event.Listener;

public interface PluginManager {
    void registerEvents(Listener listener, Plugin plugin);

    void disablePlugin(Plugin plugin);
}
