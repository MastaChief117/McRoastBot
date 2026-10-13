// APPROXIMATE stub for offline syntax/type checking ONLY.
package org.bukkit.command;

public interface CommandExecutor {
    boolean onCommand(CommandSender sender, Command command, String label, String[] args);
}
