package com.steelquill.mcroastbot.chat;

import java.util.List;
import java.util.Random;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/** Cyan chat output. Milk-brb lines are deterministic, never AI-generated. */
public final class ChatDispatcher {
    private static final List<String> MILK_LINES = List.of(
            "imma go get some milk brb dude",
            "hey is that my mum calling? i gtg brb");

    private final JavaPlugin plugin;
    private final String botName;
    private final Random random = new Random();

    public ChatDispatcher(JavaPlugin plugin, String botName) {
        this.plugin = plugin;
        this.botName = botName;
    }

    public void broadcast(String text) {
        Component msg = Component.text("<" + botName + "> " + text, NamedTextColor.AQUA);
        Bukkit.broadcast(msg);
    }

    public void milkBrb() {
        broadcast(MILK_LINES.get(random.nextInt(MILK_LINES.size())));
    }

    public JavaPlugin plugin() {
        return plugin;
    }
}
