package com.steelquill.mcroastbot.core;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Builds the exact strings sent to the model. Pure Java. */
public final class PromptBuilder {
    private PromptBuilder() {
    }

    public static String system(String template, String botName) {
        return template.replace("{BOTNAME}", botName);
    }

    public static String userBlock(EventType type, String detail, String observationCompact,
                                   List<String> diaryRecent) {
        StringBuilder sb = new StringBuilder();
        sb.append("EVENT_TYPE: ").append(type.name()).append('\n');
        if (detail != null && !detail.isBlank()) {
            sb.append("EVENT: ").append(detail.strip()).append('\n');
        }
        if (observationCompact != null && !observationCompact.isBlank()) {
            sb.append(observationCompact.strip()).append('\n');
        }
        if (diaryRecent != null && !diaryRecent.isEmpty()) {
            sb.append("RECENT ROAST DIARY FOR THIS PLAYER:\n");
            for (String r : diaryRecent) {
                sb.append("- \"").append(r).append("\"\n");
            }
            sb.append("Generate a completely fresh roast. DO NOT repeat, reword, or reuse punchlines.\n");
        }
        if (type == EventType.RANDOM) {
            sb.append("If nothing here is genuinely roastable, reply with exactly: NO_ROAST\n");
        }
        return sb.toString();
    }

    /** Opt-out list persisted as UUID strings. Pure Java. */
    public static final class OptOuts {
        private final Set<UUID> optedOut = new LinkedHashSet<>();

        public synchronized boolean isOptedOut(UUID id) {
            return optedOut.contains(id);
        }

        /** Returns true if now opted out, false if now opted in. */
        public synchronized boolean toggle(UUID id) {
            if (optedOut.contains(id)) {
                optedOut.remove(id);
                return false;
            }
            optedOut.add(id);
            return true;
        }

        public synchronized void load(List<String> raw) {
            optedOut.clear();
            if (raw == null) {
                return;
            }
            for (String s : raw) {
                try {
                    optedOut.add(UUID.fromString(s));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        public synchronized List<String> save() {
            List<String> out = new ArrayList<>(optedOut.size());
            for (UUID id : optedOut) {
                out.add(id.toString());
            }
            return out;
        }
    }
}
