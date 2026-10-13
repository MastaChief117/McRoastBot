package com.steelquill.mcroastbot.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/** Compact observation DTO. Built on the main thread, consumed async. */
public final class Observation {
    public final UUID playerId;
    public final String playerName;
    public String dimension = "unknown";
    public String biome = "unknown";
    public double health = 20.0;
    public String armor = "none";
    public String held = "empty hand";
    public final Map<String, Integer> nearby = new TreeMap<>();
    public final List<String> flags = new ArrayList<>();

    public Observation(UUID playerId, String playerName) {
        this.playerId = playerId;
        this.playerName = playerName;
    }

    public boolean interesting() {
        return !flags.isEmpty();
    }

    /** Compact token-cheap rendering for the AI prompt. */
    public String toCompact() {
        StringBuilder sb = new StringBuilder();
        sb.append("PLAYER:\nname: ").append(playerName)
          .append("\ndimension: ").append(dimension)
          .append("\nbiome: ").append(biome)
          .append("\nhealth: ").append((int) Math.round(health))
          .append("\narmor: ").append(armor)
          .append("\nheld: ").append(held);
        if (!nearby.isEmpty()) {
            sb.append("\nSURROUNDINGS:");
            for (Map.Entry<String, Integer> e : nearby.entrySet()) {
                sb.append('\n').append(e.getKey()).append(": ").append(e.getValue());
            }
        }
        if (!flags.isEmpty()) {
            sb.append("\nINTERESTING:");
            for (String f : flags) {
                sb.append('\n').append(f).append(": true");
            }
        }
        return sb.toString();
    }
}
