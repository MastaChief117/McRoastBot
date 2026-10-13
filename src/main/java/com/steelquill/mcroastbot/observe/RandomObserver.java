package com.steelquill.mcroastbot.observe;

import com.steelquill.mcroastbot.chat.RoastDispatcher;
import com.steelquill.mcroastbot.config.BotConfig;
import com.steelquill.mcroastbot.core.EventType;
import com.steelquill.mcroastbot.core.Observation;
import com.steelquill.mcroastbot.core.PromptBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Every interval (+jitter): pick ONE random eligible player and ONE random
 * parameter, gather compactly on the main thread. Silent unless interesting.
 */
public final class RandomObserver {
    private final JavaPlugin plugin;
    private final BotConfig cfg;
    private final PromptBuilder.OptOuts optOuts;
    private final RoastDispatcher dispatcher;
    private final Random random = new Random();

    public RandomObserver(JavaPlugin plugin, BotConfig cfg, PromptBuilder.OptOuts optOuts,
                          RoastDispatcher dispatcher) {
        this.plugin = plugin;
        this.cfg = cfg;
        this.optOuts = optOuts;
        this.dispatcher = dispatcher;
    }

    public void start() {
        scheduleNext();
    }

    private void scheduleNext() {
        long delay = cfg.randomIntervalSeconds
                + (cfg.randomJitterSeconds > 0 ? random.nextInt((int) cfg.randomJitterSeconds + 1) : 0);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                tick();
            } finally {
                if (plugin.isEnabled()) {
                    scheduleNext();
                }
            }
        }, delay * 20L);
    }

    private void tick() {
        List<Player> eligible = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!optOuts.isOptedOut(p.getUniqueId())) {
                eligible.add(p);
            }
        }
        if (eligible.isEmpty()) {
            return;
        }
        Player target = eligible.get(random.nextInt(eligible.size()));
        ObserveGatherer.Param param = ObserveGatherer.Param.values()[
                random.nextInt(ObserveGatherer.Param.values().length)];
        Observation ob = ObserveGatherer.gather(target, param, cfg.rareBiomes);
        if (!ob.interesting()) {
            return; // Nothing roastable: stay silent, no AI call spent.
        }
        dispatcher.roast(target.getUniqueId(), target.getName(), EventType.RANDOM,
                "random check (" + param.name().toLowerCase() + ")", ob.toCompact(),
                cfg.milkCooldownMs);
    }
}
