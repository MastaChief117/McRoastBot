package com.steelquill.mcroastbot.chat;

import com.steelquill.mcroastbot.ai.AiProvider;
import com.steelquill.mcroastbot.ai.KeyStore;
import com.steelquill.mcroastbot.config.BotConfig;
import com.steelquill.mcroastbot.core.CooldownManager;
import com.steelquill.mcroastbot.core.EventType;
import com.steelquill.mcroastbot.core.Observation;
import com.steelquill.mcroastbot.core.PromptBuilder;
import com.steelquill.mcroastbot.observe.ObserveGatherer;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Handles !-commands (chat-based so Bedrock/Geyser works identically) and
 * @botname mentions. !-messages are always cancelled, never rebroadcast.
 */
public final class ChatListener implements Listener {
    public interface AdminActions {
        String addKey(String providerId, String rawKey);

        String setKeyParty(String baseUrl, String rawKey);

        String setRotation(String mode);

        String rotationStatus();

        String setName(String name);

        String status();
    }

    private final BotConfig cfg;
    private final PromptBuilder.OptOuts optOuts;
    private final RoastDispatcher dispatcher;
    private final CooldownManager cooldowns;
    private final AdminActions admin;
    private volatile String botName;

    public ChatListener(BotConfig cfg, PromptBuilder.OptOuts optOuts, RoastDispatcher dispatcher,
                        CooldownManager cooldowns, AdminActions admin, String botName) {
        this.cfg = cfg;
        this.optOuts = optOuts;
        this.dispatcher = dispatcher;
        this.cooldowns = cooldowns;
        this.admin = admin;
        this.botName = botName;
    }

    public void setBotName(String name) {
        this.botName = name;
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        String plain = PlainTextComponentSerializer.plainText().serialize(event.message()).strip();
        Player p = event.getPlayer();
        if (plain.startsWith("!")) {
            event.setCancelled(true);
            handleBang(p, plain);
            return;
        }
        Pattern mention = Pattern.compile("@" + Pattern.quote(botName) + "\\b",
                Pattern.CASE_INSENSITIVE);
        if (mention.matcher(plain).find()) {
            if (optOuts.isOptedOut(p.getUniqueId())) {
                return; // Opt-out covers mentions too.
            }
            if (!cooldowns.tryAcquire("event:mention", cfg.eventCooldownMs)) {
                return;
            }
            Observation ob = ObserveGatherer.basic(p);
            dispatcher.roast(p.getUniqueId(), p.getName(), EventType.MENTION,
                    p.getName() + " said: " + plain, ob.toCompact(), cfg.milkCooldownMs);
        }
    }

    private void handleBang(Player p, String plain) {
        String[] parts = plain.split("\\s+", 3);
        String cmd = parts[0].toLowerCase(Locale.ROOT);
        switch (cmd) {
            case "!optout" -> {
                // /optout real command also exists; this alias works on Bedrock too.
                boolean out = optOuts.toggle(p.getUniqueId());
                p.sendMessage(out ? "McRoastBot will leave you alone."
                        : "McRoastBot is back to roasting you.");
            }
            case "!keyadd" -> {
                if (!p.isOp()) {
                    return; // OP check first, silent for non-ops.
                }
                if (parts.length < 3) {
                    p.sendMessage("Usage: !keyadd <provider> <key>");
                    return;
                }
                p.sendMessage(admin.addKey(parts[1].toLowerCase(Locale.ROOT), parts[2]));
            }
            case "!apikeyparty" -> {
                if (!p.isOp()) {
                    return;
                }
                if (parts.length < 3) {
                    p.sendMessage("Usage: !apikeyparty <base-url> <key>");
                    return;
                }
                p.sendMessage(admin.setKeyParty(parts[1], parts[2]));
            }
            case "!keyrotation" -> {
                if (!p.isOp()) {
                    return;
                }
                if (parts.length < 2 || parts[1].equalsIgnoreCase("status")) {
                    p.sendMessage(admin.rotationStatus());
                } else {
                    p.sendMessage(admin.setRotation(parts[1]));
                }
            }
            case "!changename" -> {
                if (!p.isOp()) {
                    return;
                }
                if (parts.length < 2) {
                    p.sendMessage("Usage: !changename <new-name>");
                    return;
                }
                p.sendMessage(admin.setName(parts[1]));
            }
            case "!aistatus" -> {
                if (!p.isOp()) {
                    return;
                }
                p.sendMessage(admin.status());
            }
            default -> {
                // Unknown !-message: leave cancelled, stay silent to avoid noise.
            }
        }
    }

    /** Shared OP-gated key-adding with provider validation. Never reveals keys. */
    public static String doAddKey(KeyStore keys, Map<String, AiProvider> providers,
                                  String providerId, String rawKey) {
        AiProvider prov = providers.get(providerId);
        if (prov == null) {
            return "Unknown provider. Known: " + String.join(", ", providers.keySet());
        }
        if (!prov.openAiCompatible()) {
            return prov.displayName() + " has no OpenAI-compatible endpoint in this version; "
                    + "use it via KeyParty (!apikeyparty) instead.";
        }
        if (rawKey == null || rawKey.length() < 8) {
            return "That key looks too short; not stored.";
        }
        try {
            keys.addKey(providerId, rawKey);
        } catch (Exception e) {
            return "Failed to store key (crypto error).";
        }
        int n = keys.enabledCount(providerId);
        return "Key added for " + prov.displayName() + " (" + n + " active). Full keys are never shown.";
    }

    public static boolean validBotName(String name) {
        return name != null && name.matches("[A-Za-z0-9_]{3,16}");
    }
}
