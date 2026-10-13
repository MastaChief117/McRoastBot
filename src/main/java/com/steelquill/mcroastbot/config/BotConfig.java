package com.steelquill.mcroastbot.config;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.configuration.file.FileConfiguration;

/** Typed view over config.yml. Bukkit-dependent (thin wrapper only). */
public final class BotConfig {
    public final String model;
    public final double temperature;
    public final int maxTokens;
    public final int httpTimeoutSeconds;
    public final Map<String, String> providerModels;
    public final List<String> preferredOrder;
    public final String rotationMode;
    public final int keyMaxAttempts;
    public final int maxChars;
    public final long windowMs;
    public final int tntCraft;
    public final int boatCraft;
    public final int armorStandCraft;
    public final int villagerKills;
    public final int cuteMobKills;
    public final long perPlayerCooldownMs;
    public final long globalCooldownMs;
    public final long eventCooldownMs;
    public final long milkCooldownMs;
    public final boolean randomEnabled;
    public final long randomIntervalSeconds;
    public final long randomJitterSeconds;
    public final long bountyAmount;
    public final Set<String> rareBiomes;
    public final List<String> blockedPatterns;
    public final String debugCannedReply;

    public BotConfig(FileConfiguration c) {
        model = c.getString("bot.model", "gpt-4o-mini");
        temperature = c.getDouble("bot.temperature", 0.9);
        maxTokens = c.getInt("bot.max-tokens", 120);
        httpTimeoutSeconds = c.getInt("bot.http-timeout-seconds", 60);
        providerModels = new HashMap<>();
        if (c.isConfigurationSection("bot.provider-models")) {
            for (String k : c.getConfigurationSection("bot.provider-models").getKeys(false)) {
                providerModels.put(k, c.getString("bot.provider-models." + k, ""));
            }
        }
        preferredOrder = c.getStringList("providers-preferred-order");
        rotationMode = c.getString("key-rotation", "ROUND_ROBIN");
        keyMaxAttempts = c.getInt("key-max-attempts", 4);
        maxChars = c.getInt("bot.max-chars", 200);
        windowMs = c.getInt("thresholds.window-minutes", 10) * 60_000L;
        tntCraft = c.getInt("thresholds.tnt-craft", 5);
        boatCraft = c.getInt("thresholds.boat-craft", 10);
        armorStandCraft = c.getInt("thresholds.armor-stand-craft", 12);
        villagerKills = c.getInt("thresholds.villager-kills", 3);
        cuteMobKills = c.getInt("thresholds.cute-mob-kills", 3);
        perPlayerCooldownMs = c.getInt("cooldowns.per-player-seconds", 120) * 1000L;
        globalCooldownMs = c.getInt("cooldowns.global-seconds", 45) * 1000L;
        eventCooldownMs = c.getInt("cooldowns.event-seconds", 60) * 1000L;
        milkCooldownMs = c.getInt("cooldowns.milk-brb-seconds", 90) * 1000L;
        randomEnabled = c.getBoolean("random-observer.enabled", true);
        randomIntervalSeconds = c.getInt("random-observer.interval-seconds", 180);
        randomJitterSeconds = c.getInt("random-observer.jitter-seconds", 120);
        bountyAmount = c.getLong("bounty-amount", 10000);
        rareBiomes = new HashSet<>(c.getStringList("rare-biomes"));
        blockedPatterns = c.getStringList("blocked-patterns");
        debugCannedReply = c.getString("debug-canned-reply", "");
    }
}
