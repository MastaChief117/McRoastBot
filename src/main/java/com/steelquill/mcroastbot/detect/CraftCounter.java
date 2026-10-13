package com.steelquill.mcroastbot.detect;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Sliding-window craft counting. The detector counts; the AI never sees every
 * craft event. Pure Java. Returns true once per threshold crossing.
 */
public final class CraftCounter {
    private final long windowMs;
    private final Map<UUID, Map<String, Entry>> counts = new LinkedHashMap<>();

    private record Entry(int total, long windowStart) {
    }

    public CraftCounter(long windowMs) {
        this.windowMs = windowMs;
    }

    public synchronized boolean add(UUID player, String itemKey, int amount, int threshold) {
        return add(player, itemKey, amount, threshold, System.currentTimeMillis());
    }

    /** Time-injectable overload (test hook). */
    public synchronized boolean add(UUID player, String itemKey, int amount, int threshold, long now) {
        Map<String, Entry> per = counts.computeIfAbsent(player, k -> new LinkedHashMap<>());
        Entry e = per.get(itemKey);
        if (e == null || now - e.windowStart() >= windowMs) {
            e = new Entry(0, now);
        }
        int total = e.total() + amount;
        if (total >= threshold) {
            per.remove(itemKey); // fire once per crossing
            return true;
        }
        per.put(itemKey, new Entry(total, e.windowStart()));
        return false;
    }

    public synchronized void clear(UUID player) {
        counts.remove(player);
    }
}
