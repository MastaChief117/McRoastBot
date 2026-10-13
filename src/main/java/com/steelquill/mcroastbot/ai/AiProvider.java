package com.steelquill.mcroastbot.ai;

/**
 * One chat-completions endpoint. `keyless` providers (e.g. Pollinations free
 * tier) send no Authorization header and are always available. Pure Java.
 */
public record AiProvider(
        String id,
        String displayName,
        String baseUrl,
        boolean openAiCompatible,
        boolean keyless) {
    public String completionsUrl() {
        String b = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return b + "/chat/completions";
    }
}
