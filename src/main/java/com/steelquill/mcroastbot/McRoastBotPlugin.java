package com.steelquill.mcroastbot;

import com.steelquill.mcroastbot.ai.AiGateway;
import com.steelquill.mcroastbot.ai.AiProvider;
import com.steelquill.mcroastbot.ai.KeyStore;
import com.steelquill.mcroastbot.ai.Providers;
import com.steelquill.mcroastbot.ai.RotationMode;
import com.steelquill.mcroastbot.chat.ChatDispatcher;
import com.steelquill.mcroastbot.chat.ChatListener;
import com.steelquill.mcroastbot.chat.RoastDispatcher;
import com.steelquill.mcroastbot.commands.MroastCommand;
import com.steelquill.mcroastbot.commands.OptOutCommand;
import com.steelquill.mcroastbot.config.BotConfig;
import com.steelquill.mcroastbot.core.CooldownManager;
import com.steelquill.mcroastbot.core.PromptBuilder;
import com.steelquill.mcroastbot.core.ResponseValidator;
import com.steelquill.mcroastbot.core.RoastDiary;
import com.steelquill.mcroastbot.detect.BountyManager;
import com.steelquill.mcroastbot.detect.CraftCounter;
import com.steelquill.mcroastbot.detect.KillTracker;
import com.steelquill.mcroastbot.listeners.CraftListener;
import com.steelquill.mcroastbot.listeners.DeathListener;
import com.steelquill.mcroastbot.listeners.KillListener;
import com.steelquill.mcroastbot.listeners.WorldListener;
import com.steelquill.mcroastbot.observe.RandomObserver;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * McRoastBot: chat-only AI roast bot. The bot NEVER gets OP and NEVER touches
 * the world - it only broadcasts cyan chat messages.
 * Non-final so test harnesses can boot it against a fake server.
 */
public class McRoastBotPlugin extends JavaPlugin {
    private BotConfig cfg;
    private KeyStore keys;
    private final Map<String, AiProvider> providers = new LinkedHashMap<>();
    private RotationMode rotationMode = RotationMode.ROUND_ROBIN;
    private String botName = "RoastBot";
    private final PromptBuilder.OptOuts optOuts = new PromptBuilder.OptOuts();
    private final RoastDiary diary = new RoastDiary(10);
    private RoastDispatcher dispatcher;
    private ChatListener chatListener;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        cfg = new BotConfig(getConfig());

        for (AiProvider p : Providers.defaults()) {
            providers.put(p.id(), p);
        }
        rotationMode = RotationMode.parse(cfg.rotationMode);

        try {
            keys = new KeyStore(loadOrCreateKek());
        } catch (Exception e) {
            getLogger().severe("Cannot init key storage, disabling: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        botName = loadBotFile();
        loadKeysFile();
        loadDiaryFile();
        loadOptOutFile();
        String systemTemplate = loadSystemPrompt();

        Map<String, String> models = new LinkedHashMap<>(cfg.providerModels);
        AiGateway gateway = new AiGateway(keys, providers, models, cfg.model,
                cfg.temperature, cfg.maxTokens, cfg.keyMaxAttempts, rotationMode,
                cfg.preferredOrder, cfg.httpTimeoutSeconds);
        ResponseValidator validator =
                new ResponseValidator(cfg.maxChars, 0.8, cfg.blockedPatterns);
        CooldownManager cooldowns = new CooldownManager();
        ChatDispatcher chat = new ChatDispatcher(this, botName);
        dispatcher = new RoastDispatcher(chat, gateway, diary, validator,
                cooldowns, systemTemplate, botName, cfg.perPlayerCooldownMs,
                cfg.globalCooldownMs, cfg.httpTimeoutSeconds);

        CraftCounter craftCounter = new CraftCounter(cfg.windowMs);
        KillTracker killTracker = new KillTracker(cfg.windowMs);
        BountyManager bounties = new BountyManager();

        chatListener = new ChatListener(cfg, optOuts, dispatcher, cooldowns, adminActions(), botName);
        getServer().getPluginManager().registerEvents(chatListener, this);
        getServer().getPluginManager().registerEvents(
                new CraftListener(cfg, craftCounter, cooldowns, optOuts, dispatcher), this);
        getServer().getPluginManager().registerEvents(
                new KillListener(cfg, killTracker, bounties, cooldowns, optOuts, dispatcher), this);
        getServer().getPluginManager().registerEvents(
                new WorldListener(cfg, cooldowns, optOuts, dispatcher), this);
        getServer().getPluginManager().registerEvents(
                new DeathListener(cfg, bounties, cooldowns, optOuts, dispatcher), this);
        if (getCommand("optout") != null) {
            getCommand("optout").setExecutor(new OptOutCommand(optOuts, this::saveOptOutFile));
        }
        if (getCommand("mroast") != null) {
            getCommand("mroast").setExecutor(new MroastCommand(new MroastCommand.Backend() {
                @Override
                public String status() {
                    return maskedStatus();
                }

                @Override
                public BotConfig config() {
                    return cfg;
                }

                @Override
                public RoastDispatcher dispatcher() {
                    return dispatcher;
                }
            }));
        }
        if (cfg.randomEnabled) {
            new RandomObserver(this, cfg, optOuts, dispatcher).start();
        }
        logKeySummary();
        getLogger().info("McRoastBot enabled as @" + botName + " (" + rotationMode + ")");
    }

    @Override
    public void onDisable() {
        try {
            saveKeysFile();
        } catch (Exception e) {
            getLogger().warning("Failed to save keys: " + e.getMessage());
        }
        try {
            saveDiaryFile();
        } catch (Exception e) {
            getLogger().warning("Failed to save diary: " + e.getMessage());
        }
        try {
            saveOptOutFile();
        } catch (Exception e) {
            getLogger().warning("Failed to save opt-outs: " + e.getMessage());
        }
    }

    // ---------- admin actions (OP-only, wired from ChatListener) ----------

    private ChatListener.AdminActions adminActions() {
        return new ChatListener.AdminActions() {
            @Override
            public String addKey(String providerId, String rawKey) {
                String msg = ChatListener.doAddKey(keys, providers, providerId, rawKey);
                saveKeysFile();
                return msg;
            }

            @Override
            public String setKeyParty(String baseUrl, String rawKey) {
                String clean = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1)
                        : baseUrl;
                providers.put("keyparty",
                        new AiProvider("keyparty", "KeyParty", clean, true, false, "/chat/completions"));
                String msg = ChatListener.doAddKey(keys, providers, "keyparty", rawKey);
                saveKeysFile();
                return msg + " Gateway: " + clean;
            }

            @Override
            public String setRotation(String mode) {
                RotationMode m = RotationMode.parse(mode);
                // parse() never fails; detect unknown input explicitly.
                if (!mode.equalsIgnoreCase("ROUND_ROBIN")
                        && !mode.equalsIgnoreCase("FAILOVER_PREFERRED")) {
                    return "Usage: !keyrotation <ROUND_ROBIN|FAILOVER_PREFERRED|status>";
                }
                rotationMode = m;
                return "Key rotation: " + m;
            }

            @Override
            public String rotationStatus() {
                StringBuilder sb = new StringBuilder("Rotation: " + rotationMode + ". ");
                for (String id : providers.keySet()) {
                    sb.append(id).append('=').append(keyDesc(id)).append(' ');
                }
                return sb.toString().strip();
            }

            @Override
            public String setName(String name) {
                if (!ChatListener.validBotName(name)) {
                    return "Name must be 3-16 chars: letters, digits, underscore.";
                }
                botName = name;
                saveBotFile();
                chatListener.setBotName(name);
                return "Bot name is now @" + name;
            }

            @Override
            public String status() {
                return maskedStatus();
            }
        };
    }

    private String maskedStatus() {
        // Masked counts only. Full keys are NEVER shown.
        StringBuilder sb = new StringBuilder("McRoastBot @" + botName + " keys: ");
        for (String id : providers.keySet()) {
            sb.append(id).append('=').append(keyDesc(id)).append(' ');
        }
        sb.append("| diary players=").append(diary.playerCount());
        return sb.toString();
    }

    private String keyDesc(String providerId) {
        AiProvider p = providers.get(providerId);
        if (p != null && p.keyless()) {
            return "free";
        }
        return String.valueOf(keys.enabledCount(providerId));
    }

    // ---------- persistence (server-side files, 600 where secret) ----------

    private byte[] loadOrCreateKek() throws Exception {
        File f = new File(getDataFolder(), ".kek");
        if (f.exists() && f.length() == 32) {
            return Files.readAllBytes(f.toPath());
        }
        byte[] kek = new byte[32];
        new SecureRandom().nextBytes(kek);
        getDataFolder().mkdirs();
        Files.write(f.toPath(), kek);
        restrict(f);
        return kek;
    }

    private void restrict(File f) {
        try {
            Files.setPosixFilePermissions(f.toPath(), Set.of(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (Exception ignored) {
            // Non-POSIX filesystem: best effort only.
        }
    }

    private String loadBotFile() {
        File f = new File(getDataFolder(), "bot.yml");
        if (!f.exists()) {
            return botName;
        }
        String n = YamlConfiguration.loadConfiguration(f).getString("name", botName);
        return ChatListener.validBotName(n) ? n : botName;
    }

    private void saveBotFile() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("name", botName);
        try {
            y.save(new File(getDataFolder(), "bot.yml"));
        } catch (Exception e) {
            getLogger().warning("Cannot save bot.yml: " + e.getMessage());
        }
    }

    private void loadKeysFile() {
        File f = new File(getDataFolder(), "keys.yml");
        if (!f.exists()) {
            return;
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        String kpUrl = y.getString("keyparty-url", "");
        if (!kpUrl.isBlank()) {
            providers.put("keyparty", new AiProvider("keyparty", "KeyParty", kpUrl, true, false, "/chat/completions"));
        }
        List<Map<?, ?>> list = y.getMapList("keys");
        for (Map<?, ?> m : list) {
            Object id = m.get("id");
            Object prov = m.get("provider");
            Object blob = m.get("blob");
            if (id == null || prov == null || blob == null) {
                continue;
            }
            keys.importBlob(String.valueOf(id), String.valueOf(prov), String.valueOf(blob),
                    Boolean.TRUE.equals(m.get("disabled")), String.valueOf(m.get("reason")));
        }
    }

    private void saveKeysFile() {
        YamlConfiguration y = new YamlConfiguration();
        AiProvider kp = providers.get("keyparty");
        if (kp != null) {
            y.set("keyparty-url", kp.baseUrl());
        }
        List<Map<String, Object>> list = new ArrayList<>();
        for (KeyStore.Entry e : keys.snapshot().values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", e.id());
            m.put("provider", e.providerId());
            m.put("blob", e.blob());
            m.put("disabled", e.disabled());
            m.put("reason", e.disabledReason());
            list.add(m);
        }
        y.set("keys", list);
        try {
            File f = new File(getDataFolder(), "keys.yml");
            y.save(f);
            restrict(f);
        } catch (Exception e) {
            getLogger().warning("Cannot save keys.yml: " + e.getMessage());
        }
    }

    private void loadDiaryFile() {
        File f = new File(getDataFolder(), "diary.yml");
        if (!f.exists()) {
            return;
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        Map<String, List<String>> data = new LinkedHashMap<>();
        for (String k : y.getKeys(false)) {
            data.put(k, y.getStringList(k));
        }
        diary.restore(data);
    }

    private void saveDiaryFile() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<String, List<String>> e : diary.snapshot().entrySet()) {
            y.set(e.getKey(), e.getValue());
        }
        try {
            y.save(new File(getDataFolder(), "diary.yml"));
        } catch (Exception e) {
            getLogger().warning("Cannot save diary.yml: " + e.getMessage());
        }
    }

    private void loadOptOutFile() {
        File f = new File(getDataFolder(), "optout.yml");
        if (!f.exists()) {
            return;
        }
        optOuts.load(YamlConfiguration.loadConfiguration(f).getStringList("opted-out"));
    }

    private void saveOptOutFile() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("opted-out", optOuts.save());
        try {
            y.save(new File(getDataFolder(), "optout.yml"));
        } catch (Exception e) {
            getLogger().warning("Cannot save optout.yml: " + e.getMessage());
        }
    }

    private String loadSystemPrompt() {
        try (InputStream in = getResource("system-prompt.txt")) {
            if (in != null) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            getLogger().warning("Cannot read system-prompt.txt: " + e.getMessage());
        }
        return "You are a Minecraft chat roast bot. Roast briefly, plain text.";
    }

    /** Logs per-provider key COUNTS only. Never keys, never tails. */
    private void logKeySummary() {
        StringBuilder sb = new StringBuilder("AI keys: ");
        for (Map.Entry<String, AiProvider> e : providers.entrySet()) {
            sb.append(e.getKey()).append('=').append(keyDesc(e.getKey())).append(' ');
        }
        getLogger().info(sb.toString().strip());
    }
}
