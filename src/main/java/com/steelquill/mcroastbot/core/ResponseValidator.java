package com.steelquill.mcroastbot.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Post-AI safety net: length, markdown stripping, blocklist, fake-physicality
 * claims, help-bot drift, similarity vs the roast diary. Pure Java.
 */
public final class ResponseValidator {
    public record Result(boolean ok, String text, String reason) {
        public static Result accept(String text) {
            return new Result(true, text, "ok");
        }

        public static Result silent(String reason) {
            return new Result(false, "", "silent:" + reason);
        }

        public static Result reject(String reason) {
            return new Result(false, "", reason);
        }
    }

    private static final String NO_ROAST = "NO_ROAST";

    private final int maxChars;
    private final double similarityThreshold;
    private final List<Pattern> blocked;

    public ResponseValidator(int maxChars, double similarityThreshold, List<String> extraPatterns) {
        this.maxChars = maxChars;
        this.similarityThreshold = similarityThreshold;
        this.blocked = new ArrayList<>();
        // Always-on safety patterns (case-insensitive).
        String[] builtin = {
            "kill\\s+(yourself|urself|ur\\s*self)|\\bkys\\b",
            "https?://|discord\\.gg|\\.gg/",
            "\\b\\d{1,3}(\\.\\d{1,3}){3}\\b", // no IPs
            "\\b\\d{3}-\\d{2}-\\d{4}\\b", // no SSN-like strings
            "@everyone",
            // Fake physicality: claims of actions the chat-only bot never does.
            "\\bi\\s+(teleported|punched|hit\\s+you|killed|built|placed\\s+that|am\\s+behind\\s+you)\\b",
            "my\\s+(coords|coordinates|location\\s+is)",
            // Help-bot drift.
            "here'?s\\s+how\\s+to\\s+craft|step\\s+\\d+\\s*:|place\\s+\\w+\\s+in\\s+the\\s+crafting",
        };
        for (String s : builtin) {
            blocked.add(Pattern.compile(s, Pattern.CASE_INSENSITIVE));
        }
        if (extraPatterns != null) {
            for (String s : extraPatterns) {
                try {
                    blocked.add(Pattern.compile(s, Pattern.CASE_INSENSITIVE));
                } catch (IllegalArgumentException ignored) {
                    // Bad admin regex: skip, never crash.
                }
            }
        }
    }

    public Result validate(String raw, List<String> recentForPlayer, boolean murderEvent) {
        if (raw == null) {
            return Result.reject("null");
        }
        String t = raw.strip();
        if (t.equals(NO_ROAST) || t.isEmpty()) {
            return Result.silent("no-roast");
        }
        // Single chat line: collapse whitespace, strip markdown.
        t = t.replaceAll("\\s+", " ");
        t = t.replaceAll("[*_`~#>|]", "");
        t = t.strip();
        if (t.equals(NO_ROAST) || t.isEmpty()) {
            return Result.silent("no-roast-after-clean");
        }
        for (Pattern p : blocked) {
            if (p.matcher(t).find()) {
                return Result.reject("blocked-pattern");
            }
        }
        if (recentForPlayer != null) {
            for (String prev : recentForPlayer) {
                if (prev != null && jaccard(t, prev) >= similarityThreshold) {
                    return Result.reject("too-similar");
                }
            }
        }
        if (t.length() > maxChars) {
            int cut = t.lastIndexOf(' ', maxChars);
            t = (cut > maxChars / 2 ? t.substring(0, cut) : t.substring(0, maxChars)).strip();
        }
        if (murderEvent) {
            t = t.toUpperCase(Locale.ROOT);
        }
        return Result.accept(t);
    }

    /** Token-set Jaccard similarity, 0..1. */
    static double jaccard(String a, String b) {
        Set<String> sa = tokens(a);
        Set<String> sb = tokens(b);
        if (sa.isEmpty() && sb.isEmpty()) {
            return 1.0;
        }
        Set<String> inter = new HashSet<>(sa);
        inter.retainAll(sb);
        Set<String> union = new HashSet<>(sa);
        union.addAll(sb);
        return union.isEmpty() ? 1.0 : (double) inter.size() / union.size();
    }

    private static Set<String> tokens(String s) {
        Set<String> out = new HashSet<>();
        for (String w : s.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (!w.isBlank()) {
                out.add(w);
            }
        }
        return out;
    }
}
