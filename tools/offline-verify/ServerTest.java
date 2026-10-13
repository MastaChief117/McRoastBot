package verify;

import com.steelquill.mcroastbot.McRoastBotPlugin;
import com.steelquill.mcroastbot.chat.ChatListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;

/**
 * Boots the REAL McRoastBotPlugin against a faithful fake server:
 * real config.yml, real system-prompt.txt, SnakeYAML-backed persistence,
 * inline scheduler, fake players. The closest thing to a server console
 * this sandbox allows (Mojang/Paper CDNs are unreachable here).
 */
public final class ServerTest {
    static int pass;
    static int fail;

    static void check(boolean cond, String name) {
        if (cond) {
            pass++;
            System.out.println("PASS " + name);
        } else {
            fail++;
            System.out.println("FAIL " + name);
        }
    }

    // ---------- fake server ----------

    static class FakeScheduler implements BukkitScheduler {
        int delayedScheduled;

        public void runTaskLater(Plugin p, Runnable r, long d) {
            delayedScheduled++; // recorded, NOT run (avoids observer reschedule loop)
        }

        public void runTask(Plugin p, Runnable r) {
            r.run();
        }

        public void runTaskAsynchronously(Plugin p, Runnable r) {
            r.run();
        }
    }

    static class FakePluginManager implements PluginManager {
        final List<Listener> listeners = new ArrayList<>();
        boolean disabled;

        public void registerEvents(Listener l, Plugin p) {
            listeners.add(l);
        }

        public void disablePlugin(Plugin p) {
            disabled = true;
        }
    }

    static class FakeServer implements Server {
        final FakePluginManager pm = new FakePluginManager();
        final FakeScheduler scheduler = new FakeScheduler();
        List<Player> online = new ArrayList<>();

        public PluginManager getPluginManager() {
            return pm;
        }

        public Collection<? extends Player> getOnlinePlayers() {
            return online;
        }

        public BukkitScheduler getScheduler() {
            return scheduler;
        }
    }

    static class FakePluginCommand extends PluginCommand {
        CommandExecutor exec;

        @Override
        public void setExecutor(CommandExecutor e) {
            exec = e;
        }
    }

    static class FakeConsole implements org.bukkit.command.CommandSender {
        final List<String> sent = new ArrayList<>();

        public void sendMessage(String message) {
            sent.add(message);
        }
    }

    static class TestPlugin extends McRoastBotPlugin {
        final File dataFolder;
        final FakeServer server = new FakeServer();
        final YamlConfiguration config;
        final FakePluginCommand optoutCmd = new FakePluginCommand();
        final FakePluginCommand mroastCmd = new FakePluginCommand();
        final Logger logger = Logger.getLogger("McRoastBot-test");

        TestPlugin(File dataFolder) {
            this.dataFolder = dataFolder;
            this.config = YamlConfiguration.loadConfiguration(new File(dataFolder, "config.yml"));
        }

        @Override
        public FileConfiguration getConfig() {
            return config;
        }

        @Override
        public void saveDefaultConfig() {
        }

        @Override
        public File getDataFolder() {
            return dataFolder;
        }

        @Override
        public Logger getLogger() {
            return logger;
        }

        @Override
        public Server getServer() {
            return server;
        }

        @Override
        public PluginCommand getCommand(String name) {
            if ("optout".equals(name)) {
                return optoutCmd;
            }
            return "mroast".equals(name) ? mroastCmd : null;
        }

        @Override
        public InputStream getResource(String name) {
            try {
                return new FileInputStream(new File("src/main/resources/" + name));
            } catch (Exception e) {
                return null;
            }
        }
    }

    @SuppressWarnings("unchecked")
    static <T> T findListener(FakePluginManager pm, Class<T> cls) {
        for (Listener l : pm.listeners) {
            if (cls.isInstance(l)) {
                return (T) l;
            }
        }
        return null;
    }

    public static void main(String[] args) throws Exception {
        Bukkit.scheduler = new InlineScheduler();
        Bukkit.online = new ArrayList<>();

        // Shipped config conformance: free provider first, correct default model.
        YamlConfiguration shipped = YamlConfiguration.loadConfiguration(
                new File("src/main/resources/config.yml"));
        check(shipped.getStringList("providers-preferred-order").get(0).equals("pollinations"),
                "config-pollinations-first");
        check(shipped.getConfigurationSection("bot.provider-models") != null
                && "openai-fast".equals(shipped.getString("bot.provider-models.pollinations", "")),
                "config-pollinations-model");

        File data = new File("/tmp/mcplugin-test");
        deleteRec(data);
        data.mkdirs();
        // Test config = real config.yml + test-mode canned reply enabled.
        String real = Files.readString(
                new File("src/main/resources/config.yml").toPath());
        Files.writeString(new File(data, "config.yml").toPath(),
                real.replace("debug-canned-reply: \"\"",
                        "debug-canned-reply: \"test roast supreme\""));

        System.out.println("=== boot 1 ===");
        TestPlugin plugin = new TestPlugin(data);
        plugin.onEnable();
        check(!plugin.server.pm.disabled, "boot-enables");
        check(plugin.server.pm.listeners.size() == 5, "boot-5-listeners");
        check(plugin.optoutCmd.exec != null, "boot-optout-command");
        check(plugin.mroastCmd.exec != null, "boot-mroast-command");
        check(((InlineScheduler) Bukkit.scheduler).delayedRecorded == 1,
                "boot-observer-scheduled");

        ChatListener chat = findListener(plugin.server.pm, ChatListener.class);
        check(chat != null, "boot-chat-listener");

        // OP adds a key via chat; non-OP attempt ignored.
        IntegrationTest.FakePlayer op = new IntegrationTest.FakePlayer("Admin", true);
        IntegrationTest.FakePlayer guest = new IntegrationTest.FakePlayer("Guest", false);
        Bukkit.broadcastLog.clear();
        PlainTextComponentSerializer.next = "!keyadd nvidia nv-test-secret-0123456789";
        chat.onChat(new IntegrationTest.FakeChat(op));
        check(op.sent.get(0).contains("Key added"), "console-keyadd");
        PlainTextComponentSerializer.next = "!keyadd groq sk-evil";
        chat.onChat(new IntegrationTest.FakeChat(guest));
        check(guest.sent.isEmpty(), "console-keyadd-nonop");

        // Pipeline self-test through validation -> diary -> chat.
        PlainTextComponentSerializer.next = "!aitest murder";
        chat.onChat(new IntegrationTest.FakeChat(op));
        check(Bukkit.broadcastLog.size() == 1
                && Bukkit.broadcastLog.get(0).toString().contains("TEST ROAST SUPREME"),
                "console-aitest-murder-caps");
        check(op.sent.stream().anyMatch(s -> s.contains("aitest fired (MURDER)")),
                "console-aitest-ack");

        // /mroast console command: status, non-op silence, console aitest.
        FakeConsole console = new FakeConsole();
        plugin.mroastCmd.exec.onCommand(op, new org.bukkit.command.Command(),
                "mroast", new String[]{"status"});
        check(op.sent.stream().anyMatch(s -> s.contains("pollinations=free")),
                "console-mroast-status");
        int opSent = op.sent.size();
        IntegrationTest.FakePlayer peon = new IntegrationTest.FakePlayer("Peon", false);
        plugin.mroastCmd.exec.onCommand(peon, new org.bukkit.command.Command(),
                "mroast", new String[]{"status"});
        check(peon.sent.isEmpty() && op.sent.size() == opSent, "console-mroast-nonop");
        int bc = Bukkit.broadcastLog.size();
        plugin.mroastCmd.exec.onCommand(console, new org.bukkit.command.Command(),
                "mroast", new String[]{"aitest", "murder"});
        check(Bukkit.broadcastLog.size() == bc + 1
                && Bukkit.broadcastLog.get(bc).toString().contains("TEST ROAST SUPREME"),
                "console-mroast-aitest");
        plugin.mroastCmd.exec.onCommand(console, new org.bukkit.command.Command(),
                "mroast", new String[]{"bogus"});
        check(console.sent.get(console.sent.size() - 1).contains("Usage:"),
                "console-mroast-usage");

        // Mention goes through the REAL gateway: offline it fails gracefully
        // with no broadcast; where AI is reachable a real roast may arrive.
        // Either way: no crash, no spam burst.
        PlainTextComponentSerializer.next = "@RoastBot hello";
        int before = Bukkit.broadcastLog.size();
        chat.onChat(new IntegrationTest.FakeChat(guest));
        int after = Bukkit.broadcastLog.size();
        check(after == before || after == before + 1, "console-mention-no-crash-no-spam");

        // Opt-out persists across reboot; diary + keys + name too.
        PlainTextComponentSerializer.next = "!optout";
        chat.onChat(new IntegrationTest.FakeChat(guest));
        PlainTextComponentSerializer.next = "!changename RoastLord";
        chat.onChat(new IntegrationTest.FakeChat(op));
        plugin.onDisable();
        check(new File(data, "keys.yml").exists()
                && new File(data, "diary.yml").exists()
                && new File(data, "optout.yml").exists()
                && new File(data, "bot.yml").exists(), "console-files-saved");
        String keysRaw = Files.readString(new File(data, "keys.yml").toPath());
        check(!keysRaw.contains("nv-test-secret-0123456789"), "console-no-plaintext-keys");

        System.out.println("=== boot 2 (persistence) ===");
        Bukkit.broadcastLog.clear();
        TestPlugin plugin2 = new TestPlugin(data);
        plugin2.onEnable();
        ChatListener chat2 = findListener(plugin2.server.pm, ChatListener.class);
        PlainTextComponentSerializer.next = "!aistatus";
        IntegrationTest.FakePlayer op2 = new IntegrationTest.FakePlayer("Admin", true);
        chat2.onChat(new IntegrationTest.FakeChat(op2));
        check(op2.sent.get(0).contains("nvidia=1") && op2.sent.get(0).contains("@RoastLord"),
                "console-keys-and-name-restored");
        check(op2.sent.get(0).contains("pollinations=free"), "console-pollinations-free");
        // Diary survived: same roast would now be rejected as repeat, so the
        // validator path is exercised - fire a different canned event instead.
        PlainTextComponentSerializer.next = "!aitest mention";
        chat2.onChat(new IntegrationTest.FakeChat(op2));
        check(Bukkit.broadcastLog.size() == 1, "console-diary-boot-ok");
        plugin2.onDisable();

        System.out.println("\nPASS=" + pass + " FAIL=" + fail);
        if (fail > 0) {
            System.exit(1);
        }
    }

    static class InlineScheduler implements BukkitScheduler {
        int delayedRecorded;

        public void runTaskLater(Plugin p, Runnable r, long d) {
            delayedRecorded++; // recorded, NOT run (avoids observer reschedule loop)
        }

        public void runTask(Plugin p, Runnable r) {
            r.run();
        }

        public void runTaskAsynchronously(Plugin p, Runnable r) {
            r.run();
        }
    }

    static void deleteRec(File f) {
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) {
                for (File k : kids) {
                    deleteRec(k);
                }
            }
        }
        f.delete();
    }
}
