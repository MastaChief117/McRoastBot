// APPROXIMATE stub for offline syntax/type checking ONLY.
// AsyncChatEvent extends AbstractChatEvent (verified in PaperMC/Paper source);
// the methods below come from the verified AbstractChatEvent.
package io.papermc.paper.event.player;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.player.PlayerEvent;

public class AsyncChatEvent extends PlayerEvent implements Cancellable {
    public Component message() {
        return null;
    }

    public void message(Component message) {
    }

    public boolean isCancelled() {
        return false;
    }

    public void setCancelled(boolean cancel) {
    }
}
