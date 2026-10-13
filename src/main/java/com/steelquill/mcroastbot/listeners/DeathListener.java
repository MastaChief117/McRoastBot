package com.steelquill.mcroastbot.listeners;

import com.steelquill.mcroastbot.chat.RoastDispatcher;
import com.steelquill.mcroastbot.config.BotConfig;
import com.steelquill.mcroastbot.core.CooldownManager;
import com.steelquill.mcroastbot.core.EventType;
import com.steelquill.mcroastbot.core.Observation;
import com.steelquill.mcroastbot.core.PromptBuilder;
import com.steelquill.mcroastbot.detect.BountyManager;
import com.steelquill.mcroastbot.observe.ObserveGatherer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

/** Bounty claims: a player kills a player with a bounty. Sync detect. */
public final class DeathListener implements Listener {
    private final BotConfig cfg;
    private final BountyManager bounties;
    private final CooldownManager cooldowns;
    private final PromptBuilder.OptOuts optOuts;
    private final RoastDispatcher dispatcher;

    public DeathListener(BotConfig cfg, BountyManager bounties, CooldownManager cooldowns,
                         PromptBuilder.OptOuts optOuts, RoastDispatcher dispatcher) {
        this.cfg = cfg;
        this.bounties = bounties;
        this.cooldowns = cooldowns;
        this.optOuts = optOuts;
        this.dispatcher = dispatcher;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        BountyManager.Bounty bounty = bounties.remove(victim.getUniqueId());
        if (bounty == null) {
            return;
        }
        Player killer = victim.getKiller();
        if (killer == null || killer.getUniqueId().equals(victim.getUniqueId())) {
            // Died without a player killer: bounty stays gone, no announcement.
            return;
        }
        if (optOuts.isOptedOut(killer.getUniqueId())) {
            return;
        }
        if (!cooldowns.tryAcquire("event:bounty", cfg.eventCooldownMs)) {
            return;
        }
        Observation ob = ObserveGatherer.basic(killer);
        dispatcher.roast(killer.getUniqueId(), killer.getName(), EventType.BOUNTY,
                killer.getName() + " claimed the " + bounty.amount()
                        + " doubloon bounty on " + bounty.targetName(),
                ob.toCompact(), cfg.milkCooldownMs);
    }
}
