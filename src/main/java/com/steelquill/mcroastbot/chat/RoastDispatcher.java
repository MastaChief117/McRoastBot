package com.steelquill.mcroastbot.chat;

import com.steelquill.mcroastbot.ai.AiGateway;
import com.steelquill.mcroastbot.core.CooldownManager;
import com.steelquill.mcroastbot.core.EventType;
import com.steelquill.mcroastbot.core.PromptBuilder;
import com.steelquill.mcroastbot.core.ResponseValidator;
import com.steelquill.mcroastbot.core.RoastDiary;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;

/**
 * Single funnel for every AI roast: prompt build -> async gateway ->
 * validation -> diary -> sync broadcast. Call from any thread.
 */
public final class RoastDispatcher {
    private final ChatDispatcher chat;
    private final AiGateway gateway;
    private final RoastDiary diary;
    private final ResponseValidator validator;
    private final CooldownManager cooldowns;
    private final String systemTemplate;
    private final String botName;
    private final long perPlayerCooldownMs;
    private final long globalCooldownMs;
    private final int timeoutSeconds;

    public RoastDispatcher(ChatDispatcher chat, AiGateway gateway, RoastDiary diary,
                           ResponseValidator validator, CooldownManager cooldowns,
                           String systemTemplate, String botName, long perPlayerCooldownMs,
                           long globalCooldownMs, int timeoutSeconds) {
        this.chat = chat;
        this.gateway = gateway;
        this.diary = diary;
        this.validator = validator;
        this.cooldowns = cooldowns;
        this.systemTemplate = systemTemplate;
        this.botName = botName;
        this.perPlayerCooldownMs = perPlayerCooldownMs;
        this.globalCooldownMs = globalCooldownMs;
        this.timeoutSeconds = timeoutSeconds;
    }

    /**
     * OP-only self-test path (spec test mode): feeds a canned reply through
     * validation -> diary -> broadcast without spending an AI request.
     * Broadcast hops to the main thread like every other chat output.
     */
    public void roastCanned(UUID playerId, EventType type, String detail,
                            String observationCompact, String canned) {
        List<String> recent = diary.recent(playerId);
        ResponseValidator.Result v = validator.validate(canned, recent, type.allCaps());
        if (!v.ok()) {
            chat.plugin().getLogger().info("McRoastBot aitest discarded: " + v.reason());
            return;
        }
        diary.add(playerId, v.text());
        String out = v.text();
        Bukkit.getScheduler().runTask(chat.plugin(), () -> chat.broadcast(out));
    }
    /**
     * Checks per-player + global cooldowns and fires the AI request.
     * Milk-brb fallback is sent when the global cooldown blocks an event.
     */
    public void roast(UUID playerId, String playerName, EventType type, String detail,
                      String observationCompact, long milkCooldownMs) {
        long now = System.currentTimeMillis();
        if (!cooldowns.tryAcquire("player:" + playerId, perPlayerCooldownMs, now)) {
            return;
        }
        if (!cooldowns.tryAcquire("global", globalCooldownMs, now)) {
            if (cooldowns.tryAcquire("milk", milkCooldownMs, now)) {
                Bukkit.getScheduler().runTask(chat.plugin(), chat::milkBrb);
            }
            return;
        }
        List<String> recent = diary.recent(playerId);
        String system = PromptBuilder.system(systemTemplate, botName);
        String user = PromptBuilder.userBlock(type, detail, observationCompact, recent);
        Bukkit.getScheduler().runTaskAsynchronously(chat.plugin(), () -> {
            AiGateway.CompletionResult r = gateway.complete(system, user, timeoutSeconds);
            if (!r.ok()) {
                chat.plugin().getLogger().warning("McRoastBot AI failed: " + r.error());
                return;
            }
            ResponseValidator.Result v = validator.validate(r.text(), recent, type.allCaps());
            if (!v.ok()) {
                if (!v.reason().startsWith("silent:")) {
                    chat.plugin().getLogger().info("McRoastBot discarded AI reply: " + v.reason());
                }
                return;
            }
            diary.add(playerId, v.text());
            String out = v.text();
            Bukkit.getScheduler().runTask(chat.plugin(), () -> chat.broadcast(out));
        });
    }
}
