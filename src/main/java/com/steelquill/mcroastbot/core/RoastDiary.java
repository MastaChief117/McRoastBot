package com.steelquill.mcroastbot.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Small per-player "roast diary": the last N AI replies per player, sent back
 * as context so the model cannot repeat itself. Pure Java, no Bukkit.
 */
public final class RoastDiary {
    private final int capacity;
    private final Map<UUID, Deque<String>> diary = new LinkedHashMap<>();

    public RoastDiary(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be >= 1");
        }
        this.capacity = capacity;
    }

    public synchronized void add(UUID player, String roast) {
        Deque<String> q = diary.computeIfAbsent(player, k -> new ArrayDeque<>());
        q.addLast(roast);
        while (q.size() > capacity) {
            q.removeFirst();
        }
    }

    public synchronized List<String> recent(UUID player) {
        Deque<String> q = diary.get(player);
        return q == null ? List.of() : List.copyOf(q);
    }

    public synchronized int playerCount() {
        return diary.size();
    }

    /** Snapshot with string keys for YAML persistence. */
    public synchronized Map<String, List<String>> snapshot() {
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (Map.Entry<UUID, Deque<String>> e : diary.entrySet()) {
            out.put(e.getKey().toString(), new ArrayList<>(e.getValue()));
        }
        return out;
    }

    public synchronized void restore(Map<String, List<String>> data) {
        diary.clear();
        if (data == null) {
            return;
        }
        for (Map.Entry<String, List<String>> e : data.entrySet()) {
            try {
                UUID id = UUID.fromString(e.getKey());
                Deque<String> q = new ArrayDeque<>();
                List<String> vals = e.getValue() == null ? List.of() : e.getValue();
                for (String s : vals) {
                    if (s != null && !s.isBlank()) {
                        q.addLast(s);
                    }
                }
                while (q.size() > capacity) {
                    q.removeFirst();
                }
                if (!q.isEmpty()) {
                    diary.put(id, q);
                }
            } catch (IllegalArgumentException ignored) {
                // Bad UUID key in file: skip, never crash the plugin.
            }
        }
    }
}
