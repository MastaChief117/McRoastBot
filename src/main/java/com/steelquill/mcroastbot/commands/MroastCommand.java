package com.steelquill.mcroastbot.commands;

import com.steelquill.mcroastbot.chat.RoastDispatcher;
import com.steelquill.mcroastbot.config.BotConfig;
import com.steelquill.mcroastbot.core.EventType;
import java.util.Locale;
import java.util.UUID;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /mroast - console + OP admin command. The console cannot send !-chat
 * commands, so this is its way to check status and drive test roasts.
 * Also the hook CI uses to test the plugin on a live server.
 */
public final class MroastCommand implements CommandExecutor {
    public interface Backend {
        String status();

        BotConfig config();

        RoastDispatcher dispatcher();
    }

    private static final UUID CONSOLE_ID = new UUID(0L, 0L);
    private final Backend backend;

    public MroastCommand(Backend backend) {
        this.backend = backend;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player p && !p.isOp()) {
            return true; // silent for non-ops, same policy as !-commands
        }
        if (args.length == 0) {
            sender.sendMessage("Usage: /mroast <status|aitest|live> [event-type]");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "status" -> sender.sendMessage(backend.status());
            case "aitest" -> {
                String canned = backend.config().debugCannedReply;
                if (canned == null || canned.isBlank()) {
                    sender.sendMessage("Test mode disabled (debug-canned-reply is empty).");
                    return true;
                }
                EventType t = parseType(args, sender);
                if (t == null) {
                    return true;
                }
                backend.dispatcher().roastCanned(CONSOLE_ID, t, "console self-test",
                        "PLAYER:\nname: console", canned);
                sender.sendMessage("aitest fired (" + t.name() + ").");
            }
            case "live" -> {
                EventType t = parseType(args, sender);
                if (t == null) {
                    return true;
                }
                backend.dispatcher().roast(CONSOLE_ID, "console", t, "console live test",
                        "PLAYER:\nname: console", backend.config().milkCooldownMs);
                sender.sendMessage("live request fired (" + t.name() + ") - watch chat.");
            }
            default -> sender.sendMessage("Usage: /mroast <status|aitest|live> [event-type]");
        }
        return true;
    }

    private EventType parseType(String[] args, CommandSender sender) {
        if (args.length < 2) {
            return EventType.MENTION;
        }
        try {
            return EventType.valueOf(args[1].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            sender.sendMessage("Unknown type. Use: mention|craft|murder|bounty|dimension|random");
            return null;
        }
    }
}
