package com.steelquill.mcroastbot.core;

import java.util.concurrent.ConcurrentHashMap;

/** Multi-layer anti-spam gate. Pure Java, no Bukkit. Thread-safe. */
public final class CooldownManager {
    private final ConcurrentHashMap<String, Long> last = new ConcurrentHashMap<>();

    public boolean tryAcquire(String key, long cooldownMs) {
        return tryAcquire(key, cooldownMs, System.currentTimeMillis());
    }

    public boolean tryAcquire(String key, long cooldownMs, long nowMs) {
        Long prev = last.get(key);
        if (prev != null && nowMs - prev < cooldownMs) {
            return false;
        }
        last.put(key, nowMs);
        return true;
    }

    public long remainingMs(String key, long cooldownMs, long nowMs) {
        Long prev = last.get(key);
        if (prev == null) {
            return 0;
        }
        return Math.max(0, cooldownMs - (nowMs - prev));
    }

    public void reset(String key) {
        last.remove(key);
    }

    public void clear() {
        last.clear();
    }
}
