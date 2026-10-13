package com.steelquill.mcroastbot.core;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Event types the AI can be asked about. Determines caps/formatting. */
public enum EventType {
    CRAFT(false),
    MURDER(true),
    BOUNTY(true),
    DIMENSION(false),
    RANDOM(false),
    MENTION(false);

    private final boolean allCaps;

    EventType(boolean allCaps) {
        this.allCaps = allCaps;
    }

    public boolean allCaps() {
        return allCaps;
    }
}
