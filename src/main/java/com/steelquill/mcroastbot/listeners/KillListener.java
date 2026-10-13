package com.steelquill.mcroastbot.listeners;

import com.steelquill.mcroastbot.chat.RoastDispatcher;
import com.steelquill.mcroastbot.config.BotConfig;
import com.steelquill.mcroastbot.core.CooldownManager;
import com.steelquill.mcroastbot.core.EventType;
import com.steelquill.mcroastbot.core.Observation;
import com.steelquill.mcroastbot.core.PromptBuilder;
import com.steelquill.mcroastbot.detect.BountyManager;
import com.steelquill.mcroastbot.detect.KillTracker;
import com.steelquill.mcroastbot.observe.ObserveGatherer;
import java.util.EnumSet;
import java.util.Set;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

/** Mass-murder detector: villagers, cute mobs, babies. Sets bounties. Sync. */
public final class KillListener implements Listener {
    private static final Set<org.bukkit.entity.EntityType> CUTE = EnumSet.of(
            org.bukkit.entity.EntityType.AXOLOTL,
            org.bukkit.entity.EntityType.DOLPHIN,
            org.bukkit.entity.EntityType.TURTLE,
            org.bukkit.entity.EntityType.CAT,
            org.bukkit.entity.EntityType.PANDA,
            org.bukkit.entity.EntityType.PARROT,
            org.bukkit.entity.EntityType.RABBIT,
            org.bukkit.entity.EntityType.FOX,
            org.bukkit.entity.EntityType.FROG,
            org.bukkit.entity.EntityType.OCELOT,
            org.bukkit.entity.EntityType.SNIFFER);

    private final BotConfig cfg;
    private final KillTracker tracker;
    private final BountyManager bounties;
    private final CooldownManager cooldowns;
    private final PromptBuilder.OptOuts optOuts;
    private final RoastDispatcher dispatcher;

    public KillListener(BotConfig cfg, KillTracker tracker, BountyManager bounties,
                        CooldownManager cooldowns, PromptBuilder.OptOuts optOuts,
                        RoastDispatcher dispatcher) {
        this.cfg = cfg;
        this.tracker = tracker;
        this.bounties = bounties;
        this.cooldowns = cooldowns;
        this.optOuts = optOuts;
        this.dispatcher = dispatcher;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        Player killer = dead.getKiller();
        if (killer == null) {
            try {
                Entity causing = event.getDamageSource().getCausingEntity();
                if (causing instanceof Player pl) {
                    killer = pl;
                }
            } catch (Exception ignored) {
            }
        }
        if (killer == null || optOuts.isOptedOut(killer.getUniqueId())) {
            return;
        }
        boolean villager = dead.getType() == org.bukkit.entity.EntityType.VILLAGER;
        boolean baby = dead instanceof Ageable age && !age.isAdult();
        boolean cute = CUTE.contains(dead.getType());
        boolean fired = false;
        String what = "";
        if (villager && tracker.add(killer.getUniqueId(), KillTracker.Category.VILLAGER, cfg.villagerKills)) {
            fired = true;
            what = "villagers";
        } else if ((cute || baby) && tracker.add(killer.getUniqueId(),
                baby ? KillTracker.Category.BABY : KillTracker.Category.CUTE, cfg.cuteMobKills)) {
            fired = true;
            what = baby ? "baby mobs" : "cute mobs";
        }
        if (!fired) {
            return;
        }
        bounties.setBounty(killer.getUniqueId(), killer.getName(), cfg.bountyAmount);
        if (!cooldowns.tryAcquire("event:murder", cfg.eventCooldownMs)) {
            return;
        }
        Observation ob = ObserveGatherer.basic(killer);
        dispatcher.roast(killer.getUniqueId(), killer.getName(), EventType.MURDER,
                killer.getName() + " mass-killed " + what
                        + ". bounty of " + cfg.bountyAmount + " doubloons on their head",
                ob.toCompact(), cfg.milkCooldownMs);
    }
}
