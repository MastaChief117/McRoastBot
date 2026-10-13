package com.steelquill.mcroastbot.commands;

import com.steelquill.mcroastbot.core.PromptBuilder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /optout - player-only toggle. !optout chat alias lives in ChatListener. */
public final class OptOutCommand implements CommandExecutor {
    public interface Saver {
        void save();
    }

    private final PromptBuilder.OptOuts optOuts;
    private final Saver saver;

    public OptOutCommand(PromptBuilder.OptOuts optOuts, Saver saver) {
        this.optOuts = optOuts;
        this.saver = saver;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Only players can opt out.");
            return true;
        }
        boolean out = optOuts.toggle(p.getUniqueId());
        saver.save();
        p.sendMessage(out ? "McRoastBot will leave you alone."
                : "McRoastBot is back to roasting you.");
        return true;
    }
}
