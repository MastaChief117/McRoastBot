package com.steelquill.mcroastbot.ai;

import java.util.List;

/**
 * The 10 required providers (incl. NVIDIA NIM) plus KeyParty as a custom
 * OpenAI-compatible gateway. Base URLs are the vendors' documented
 * OpenAI-compatible endpoints; Anthropic's native API is NOT OpenAI-compatible
 * so direct mode is disabled in the MVP - use it via KeyParty instead.
 */
public final class Providers {
    private Providers() {
    }

    public static final AiProvider OPENAI =
            new AiProvider("openai", "OpenAI", "https://api.openai.com/v1", true);
    public static final AiProvider NVIDIA =
            new AiProvider("nvidia", "NVIDIA NIM", "https://integrate.api.nvidia.com/v1", true);
    public static final AiProvider GROQ =
            new AiProvider("groq", "Groq", "https://api.groq.com/openai/v1", true);
    public static final AiProvider GEMINI =
            new AiProvider("gemini", "Gemini", "https://generativelanguage.googleapis.com/v1beta/openai/", true);
    public static final AiProvider TOGETHER =
            new AiProvider("together", "Together", "https://api.together.xyz/v1", true);
    public static final AiProvider DEEPSEEK =
            new AiProvider("deepseek", "DeepSeek", "https://api.deepseek.com/v1", true);
    public static final AiProvider OPENROUTER =
            new AiProvider("openrouter", "OpenRouter", "https://openrouter.ai/api/v1", true);
    public static final AiProvider FIREWORKS =
            new AiProvider("fireworks", "Fireworks", "https://api.fireworks.ai/inference/v1", true);
    public static final AiProvider MISTRAL =
            new AiProvider("mistral", "Mistral", "https://api.mistral.ai/v1", true);
    public static final AiProvider ANTHROPIC =
            new AiProvider("anthropic", "Anthropic", "https://api.anthropic.com/v1", false);
    /** KeyParty base URL is operator-configured via !apikeyparty. Default is local. */
    public static final AiProvider KEYPARTY =
            new AiProvider("keyparty", "KeyParty", "http://localhost:8080/v1", true);

    public static List<AiProvider> defaults() {
        return List.of(OPENAI, NVIDIA, GROQ, GEMINI, TOGETHER, DEEPSEEK,
                OPENROUTER, FIREWORKS, MISTRAL, ANTHROPIC, KEYPARTY);
    }
}
