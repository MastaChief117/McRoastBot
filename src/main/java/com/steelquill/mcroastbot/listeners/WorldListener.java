package com.steelquill.mcroastbot.listeners;

import com.steelquill.mcroastbot.chat.RoastDispatcher;
import com.steelquill.mcroastbot.config.BotConfig;
import com.steelquill.mcroastbot.core.CooldownManager;
import com.steelquill.mcroastbot.core.EventType;
import com.steelquill.mcroastbot.core.Observation;
import com.steelquill.mcroastbot.core.PromptBuilder;
import com.steelquill.mcroastbot.observe.ObserveGatherer;
import java.util.Locale;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/** Dimension / rare-biome entry detector. Sync. */
public final class WorldListener implements Listener {
    private final BotConfig cfg;
    private final CooldownManager cooldowns;
    private final PromptBuilder.OptOuts optOuts;
    private final RoastDispatcher dispatcher;

    public WorldListener(BotConfig cfg, CooldownManager cooldowns,
                         PromptBuilder.OptOuts optOuts, RoastDispatcher dispatcher) {
        this.cfg = cfg;
        this.cooldowns = cooldowns;
        this.optOuts = optOuts;
        this.dispatcher = dispatcher;
    }

    @EventHandler(ignoreCancelled = true)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        check(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getTo() == null || event.getTo().getWorld() == null) {
            return;
        }
        if (event.getTo().getWorld().equals(event.getFrom().getWorld())) {
            return; // Same-world teleport: not a dimension/biome entry.
        }
        check(event.getPlayer());
    }

    private void check(Player p) {
        if (optOuts.isOptedOut(p.getUniqueId())) {
            return;
        }
        World.Environment env;
        String biome;
        try {
            env = p.getWorld().getEnvironment();
            biome = p.getLocation().getBlock().getBiome().name().toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            return;
        }
        boolean dangerous = env == World.Environment.NETHER || env == World.Environment.THE_END;
        boolean rare = cfg.rareBiomes.contains(biome);
        if (!dangerous && !rare) {
            return;
        }
        if (!cooldowns.tryAcquire("event:dimension", cfg.eventCooldownMs)) {
            return;
        }
        Observation ob = ObserveGatherer.basic(p);
        dispatcher.roast(p.getUniqueId(), p.getName(), EventType.DIMENSION,
                p.getName() + " entered " + env.name().toLowerCase(Locale.ROOT)
                        + " (" + biome + ")", ob.toCompact(), cfg.milkCooldownMs);
    }
}
