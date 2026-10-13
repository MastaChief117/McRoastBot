package com.steelquill.mcroastbot.ai;

/** One chat-completions endpoint. Pure Java. */
public record AiProvider(
        String id,
        String displayName,
        String baseUrl,
        boolean openAiCompatible) {
    public String completionsUrl() {
        String b = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return b + "/chat/completions";
    }
}
