package com.steelquill.mcroastbot.listeners;

import com.steelquill.mcroastbot.chat.RoastDispatcher;
import com.steelquill.mcroastbot.config.BotConfig;
import com.steelquill.mcroastbot.core.CooldownManager;
import com.steelquill.mcroastbot.core.EventType;
import com.steelquill.mcroastbot.core.Observation;
import com.steelquill.mcroastbot.core.PromptBuilder;
import com.steelquill.mcroastbot.detect.CraftCounter;
import com.steelquill.mcroastbot.observe.ObserveGatherer;
import java.util.Locale;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.ItemStack;

/** Spam-crafting detector: counts cross thresholds, then roasts. Sync. */
public final class CraftListener implements Listener {
    private final BotConfig cfg;
    private final CraftCounter counter;
    private final CooldownManager cooldowns;
    private final PromptBuilder.OptOuts optOuts;
    private final RoastDispatcher dispatcher;

    public CraftListener(BotConfig cfg, CraftCounter counter, CooldownManager cooldowns,
                         PromptBuilder.OptOuts optOuts, RoastDispatcher dispatcher) {
        this.cfg = cfg;
        this.counter = counter;
        this.cooldowns = cooldowns;
        this.optOuts = optOuts;
        this.dispatcher = dispatcher;
    }

    @EventHandler(ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)) {
            return;
        }
        if (optOuts.isOptedOut(p.getUniqueId())) {
            return;
        }
        ItemStack result;
        try {
            result = event.getRecipe().getResult();
        } catch (Exception e) {
            return;
        }
        String mat = result.getType().name();
        String key = null;
        int threshold = 0;
        if (mat.equals("TNT")) {
            key = "tnt";
            threshold = cfg.tntCraft;
        } else if (mat.endsWith("_BOAT")) {
            key = "boat";
            threshold = cfg.boatCraft;
        } else if (mat.equals("ARMOR_STAND")) {
            key = "armor_stand";
            threshold = cfg.armorStandCraft;
        }
        if (key == null) {
            return;
        }
        int amount = result.getAmount() * (event.isShiftClick() ? shiftEstimate(event) : 1);
        if (!counter.add(p.getUniqueId(), key, amount, threshold)) {
            return;
        }
        if (!cooldowns.tryAcquire("event:craft", cfg.eventCooldownMs)) {
            return;
        }
        Observation ob = ObserveGatherer.basic(p);
        dispatcher.roast(p.getUniqueId(), p.getName(), EventType.CRAFT,
                p.getName() + " mass-crafted " + mat.toLowerCase(Locale.ROOT) + " x" + amount,
                ob.toCompact(), cfg.milkCooldownMs);
    }

    /** Rough shift-click yield: smallest ingredient stack, capped. */
    private static int shiftEstimate(CraftItemEvent event) {
        try {
            int min = Integer.MAX_VALUE;
            for (ItemStack s : event.getInventory().getMatrix()) {
                if (s != null && !s.getType().isAir()) {
                    min = Math.min(min, s.getAmount());
                }
            }
            if (min == Integer.MAX_VALUE) {
                return 1;
            }
            return Math.max(1, Math.min(min, 64));
        } catch (Exception e) {
            return 1;
        }
    }
}
