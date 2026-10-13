package com.steelquill.mcroastbot.ai;

/**
 * Minimal OpenAI-compatible JSON builder/parser with zero dependencies.
 * Manual escaping keeps the jar dependency-free; swap for Gson later if wanted.
 */
public final class ChatPayload {
    private ChatPayload() {
    }

    public static String buildRequest(String model, String system, String user,
                                      double temperature, int maxTokens) {
        return "{\"model\":\"" + esc(model) + "\","
                + "\"temperature\":" + temperature + ","
                + "\"max_tokens\":" + maxTokens + ","
                + "\"messages\":["
                + "{\"role\":\"system\",\"content\":\"" + esc(system) + "\"},"
                + "{\"role\":\"user\",\"content\":\"" + esc(user) + "\"}"
                + "]}";
    }

    /** Extract choices[0].message.content, or null when absent/unparseable. */
    public static String extractContent(String json) {
        if (json == null) {
            return null;
        }
        int ci = json.indexOf("\"choices\"");
        if (ci < 0) {
            return null;
        }
        int mi = json.indexOf("\"content\"", ci);
        if (mi < 0) {
            return null;
        }
        int colon = json.indexOf(':', mi);
        if (colon < 0) {
            return null;
        }
        int q = json.indexOf('"', colon);
        if (q < 0) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = q + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"') {
                return sb.toString();
            }
            if (c == '\\' && i + 1 < json.length()) {
                char n = json.charAt(++i);
                switch (n) {
                    case 'n' -> sb.append('\n');
                    case 't' -> sb.append('\t');
                    case 'r' -> sb.append('\r');
                    case 'u' -> {
                        if (i + 4 < json.length()) {
                            try {
                                sb.append((char) Integer.parseInt(json.substring(i + 1, i + 5), 16));
                                i += 4;
                            } catch (NumberFormatException e) {
                                sb.append('?');
                            }
                        }
                    }
                    default -> sb.append(n);
                }
            } else {
                sb.append(c);
            }
        }
        return null;
    }

    /** Classify HTTP outcome: retry-with-next-key vs disable-vs-fatal. */
    public enum Outcome { OK, ROTATE_NEXT, DISABLE_KEY, FATAL }

    public static Outcome classify(int httpStatus) {
        if (httpStatus == 200) {
            return Outcome.OK;
        }
        if (httpStatus == 401 || httpStatus == 403) {
            return Outcome.DISABLE_KEY;
        }
        if (httpStatus == 400 || httpStatus == 404 || httpStatus == 422) {
            return Outcome.FATAL;
        }
        // 429 quota/rate-limit, 402 payment, 5xx overload: rotate to next key.
        return Outcome.ROTATE_NEXT;
    }

    static String esc(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
