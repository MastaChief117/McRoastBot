// APPROXIMATE stub for offline syntax/type checking ONLY (network-limited sandbox).
// Real signatures verified against PaperMC/Paper GitHub source where risky.
// Excluded from the Gradle build (lives outside src/main/java).
package org.bukkit.plugin;

import org.bukkit.event.Listener;

public interface Plugin {
    boolean isEnabled();
}
