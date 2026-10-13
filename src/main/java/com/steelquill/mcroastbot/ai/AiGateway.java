package com.steelquill.mcroastbot.ai;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * OpenAI-compatible gateway client with key rotation + provider failover.
 * Network only happens here. Call from async threads, never the main thread.
 */
/** Non-final so tests/operators can subclass (e.g. canned or logging gateway). */
public class AiGateway {
    public record CompletionResult(boolean ok, String text, String providerId,
                                   boolean retryable, String error) {
    }

    private final HttpClient http;
    private final KeyStore keys;
    private final Map<String, AiProvider> providers;
    private final Map<String, String> providerModels;
    private final String defaultModel;
    private final double temperature;
    private final int maxTokens;
    private final int maxAttempts;
    private final RotationMode mode;
    private final List<String> preferredOrder;

    public AiGateway(KeyStore keys, Map<String, AiProvider> providers,
                     Map<String, String> providerModels, String defaultModel,
                     double temperature, int maxTokens, int maxAttempts,
                     RotationMode mode, List<String> preferredOrder, int timeoutSeconds) {
        this.keys = keys;
        this.providers = providers;
        this.providerModels = providerModels;
        this.defaultModel = defaultModel;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.mode = mode;
        this.preferredOrder = List.copyOf(preferredOrder);
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(5, timeoutSeconds)))
                .build();
    }

    public CompletionResult complete(String system, String user, int timeoutSeconds) {
        List<String> order = providerOrder();
        int attempts = 0;
        String lastError = "no keys configured";
        for (String providerId : order) {
            AiProvider p = providers.get(providerId);
            if (p == null || !p.openAiCompatible()) {
                continue;
            }
            // Keyless providers (Pollinations free tier) always get one attempt.
            for (int k = 0; k < Math.max(1, keys.enabledCount(providerId)); k++) {
                KeyStore.Entry e = keys.next(providerId);
                if (e == null && !p.keyless()) {
                    break; // No keys and not keyless: skip provider, spend no attempt.
                }
                String raw = null;
                if (e != null) {
                    try {
                        raw = keys.decrypt(e);
                    } catch (Exception ex) {
                        lastError = "decrypt-failed:" + providerId;
                        continue;
                    }
                }
                if (attempts >= maxAttempts) {
                    return new CompletionResult(false, "", "", true, lastError);
                }
                attempts++;
                String model = providerModels.getOrDefault(providerId, defaultModel);
                try {
                    HttpRequest.Builder builder = HttpRequest.newBuilder()
                            .uri(URI.create(p.completionsUrl()))
                            .timeout(Duration.ofSeconds(Math.max(5, timeoutSeconds)))
                            .header("Content-Type", "application/json")
                            .header("User-Agent", "McRoastBot/0.1.0");
                    if (raw != null) {
                        builder.header("Authorization", "Bearer " + raw);
                    }
                    HttpRequest req = builder.POST(HttpRequest.BodyPublishers.ofString(
                                    ChatPayload.buildRequest(model, system, user, temperature, maxTokens)))
                            .build();
                    HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
                    String hint = errorHint(res.body());
                    if (res.statusCode() == 404 && isUnknownModel(res.body())) {
                        // Wrong model name for this provider: try the next one.
                        lastError = "unknown-model:" + providerId + hint;
                        continue;
                    }
                    switch (ChatPayload.classify(res.statusCode())) {
                        case OK -> {
                            String text = ChatPayload.extractContent(res.body());
                            if (text == null || text.isBlank()) {
                                lastError = "empty-response:" + providerId + hint;
                            } else {
                                return new CompletionResult(true, text, providerId, false, "");
                            }
                        }
                        case DISABLE_KEY -> {
                            if (e != null) {
                                keys.markInvalid(e.id());
                            }
                            lastError = "invalid-key:" + providerId + hint;
                        }
                        case ROTATE_NEXT -> {
                            if (e != null) {
                                keys.markTransientFailure(e.id());
                            }
                            lastError = "transient:" + providerId + ":" + res.statusCode() + hint;
                        }
                        case FATAL -> {
                            lastError = "fatal:" + providerId + ":" + res.statusCode() + hint;
                            return new CompletionResult(false, "", providerId, false, lastError);
                        }
                    }
                } catch (Exception ex) {
                    if (e != null) {
                        keys.markTransientFailure(e.id());
                    }
                    lastError = "io-error:" + providerId;
                }
            }
        }
        return new CompletionResult(false, "", "", true, lastError);
    }

    private List<String> providerOrder() {
        List<String> base;
        if (mode == RotationMode.FAILOVER_PREFERRED && !preferredOrder.isEmpty()) {
            base = new ArrayList<>(preferredOrder);
            for (String id : providers.keySet()) {
                if (!base.contains(id)) {
                    base.add(id);
                }
            }
        } else {
            base = new ArrayList<>(providers.keySet());
        }
        // Keyless providers (free, no setup) always go first.
        List<String> keyless = new ArrayList<>();
        List<String> keyed = new ArrayList<>();
        for (String id : base) {
            AiProvider p = providers.get(id);
            if (p != null && p.keyless() && p.openAiCompatible()) {
                keyless.add(id);
            } else {
                keyed.add(id);
            }
        }
        keyless.addAll(keyed);
        return keyless;
    }

    /** First 160 chars of an error body, single line. Never contains keys. */
    public static String errorHint(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        String oneLine = body.replaceAll("\\s+", " ").strip();
        return " :: " + oneLine.substring(0, Math.min(160, oneLine.length()));
    }

    /** 404 that complains about the model name: rotate, don't hard-fail. */
    public static boolean isUnknownModel(String body) {
        return body != null && body.toLowerCase(java.util.Locale.ROOT).contains("model");
    }
}
