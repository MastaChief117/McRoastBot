package com.steelquill.mcroastbot.detect;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Murder bounties: set on mass-murder, cleared when the murderer dies to a player. Pure Java. */
public final class BountyManager {
    public record Bounty(UUID targetId, String targetName, long amount) {
    }

    private final Map<UUID, Bounty> bounties = new LinkedHashMap<>();

    public synchronized void setBounty(UUID targetId, String targetName, long amount) {
        bounties.put(targetId, new Bounty(targetId, targetName, amount));
    }

    public synchronized boolean hasBounty(UUID targetId) {
        return bounties.containsKey(targetId);
    }

    public synchronized Bounty remove(UUID targetId) {
        return bounties.remove(targetId);
    }

    public synchronized int size() {
        return bounties.size();
    }
}
