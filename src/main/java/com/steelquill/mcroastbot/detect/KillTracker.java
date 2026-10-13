package com.steelquill.mcroastbot.detect;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Sliding-window kill counting per category. Pure Java. */
public final class KillTracker {
    public enum Category { VILLAGER, CUTE, BABY }

    private record Slot(int count, long windowStart) {
    }

    private final long windowMs;
    private final Map<UUID, Map<Category, Slot>> counts = new LinkedHashMap<>();

    public KillTracker(long windowMs) {
        this.windowMs = windowMs;
    }

    /** Returns true once when the category count crosses the threshold. */
    public synchronized boolean add(UUID killer, Category cat, int threshold) {
        return add(killer, cat, threshold, System.currentTimeMillis());
    }

    /** Time-injectable overload (test hook). */
    public synchronized boolean add(UUID killer, Category cat, int threshold, long now) {
        Map<Category, Slot> per = counts.computeIfAbsent(killer, k -> new LinkedHashMap<>());
        Slot s = per.get(cat);
        if (s == null || now - s.windowStart() >= windowMs) {
            s = new Slot(0, now);
        }
        int count = s.count() + 1;
        if (count >= threshold) {
            per.remove(cat); // fire once per crossing
            return true;
        }
        per.put(cat, new Slot(count, s.windowStart()));
        return false;
    }

    public synchronized void clear(UUID player) {
        counts.remove(player);
    }
}
