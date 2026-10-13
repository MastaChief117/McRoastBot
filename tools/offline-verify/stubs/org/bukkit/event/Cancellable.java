// APPROXIMATE stub for offline syntax/type checking ONLY.
package org.bukkit.event;

public interface Cancellable {
    boolean isCancelled();

    void setCancelled(boolean cancel);
}
