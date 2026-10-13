package verify;

import com.steelquill.mcroastbot.ai.AiGateway;
import com.steelquill.mcroastbot.ai.AiProvider;
import com.steelquill.mcroastbot.ai.KeyStore;
import com.steelquill.mcroastbot.ai.Providers;
import com.steelquill.mcroastbot.ai.RotationMode;
import com.steelquill.mcroastbot.chat.ChatDispatcher;
import com.steelquill.mcroastbot.chat.ChatListener;
import com.steelquill.mcroastbot.chat.RoastDispatcher;
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
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.Recipe;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;

/**
 * Offline integration test: fake players/events drive the REAL listeners
 * through the REAL dispatcher with a canned AI. No server, no network.
 */
public final class IntegrationTest {
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

    // ---------------- fakes ----------------

    static class InlineScheduler implements BukkitScheduler {
        public void runTaskLater(Plugin p, Runnable r, long d) {
            r.run();
        }

        public void runTask(Plugin p, Runnable r) {
            r.run();
        }

        public void runTaskAsynchronously(Plugin p, Runnable r) {
            r.run();
        }
    }

    static class FakeStack extends ItemStack {
        final Material type;
        final int amount;

        FakeStack(Material type, int amount) {
            this.type = type;
            this.amount = amount;
        }

        @Override
        public Material getType() {
            return type;
        }

        @Override
        public int getAmount() {
            return amount;
        }
    }

    static class FakeInv implements PlayerInventory {
        ItemStack[] armor = new ItemStack[4];
        ItemStack hand = new FakeStack(Material.AIR, 0);

        public ItemStack[] getArmorContents() {
            return armor;
        }

        public ItemStack getItemInMainHand() {
            return hand;
        }
    }

    static class FakeBlock implements Block {
        final Material type;
        final Biome biome;

        FakeBlock(Material type, Biome biome) {
            this.type = type;
            this.biome = biome;
        }

        public Material getType() {
            return type;
        }

        public Biome getBiome() {
            return biome;
        }
    }

    static class FakeWorld implements World {
        final Environment env;

        FakeWorld(Environment env) {
            this.env = env;
        }

        public Environment getEnvironment() {
            return env;
        }

        public Collection<Entity> getNearbyEntities(Location l, double x, double y, double z) {
            return List.of();
        }

        public Block getBlockAt(int x, int y, int z) {
            return new FakeBlock(Material.AIR, Biome.PLAINS);
        }
    }

    static class FakeLocation extends Location {
        final FakeWorld world;
        final Biome biome;

        FakeLocation(FakeWorld world, Biome biome) {
            this.world = world;
            this.biome = biome;
        }

        @Override
        public World getWorld() {
            return world;
        }

        @Override
        public Block getBlock() {
            return new FakeBlock(Material.AIR, biome);
        }
    }

    static class FakePlayer implements Player {
        final UUID id = UUID.randomUUID();
        final String name;
        boolean op;
        final FakeInv inv = new FakeInv();
        FakeWorld world = new FakeWorld(World.Environment.NORMAL);
        Biome biome = Biome.PLAINS;
        Player killer;
        final List<String> sent = new ArrayList<>();

        FakePlayer(String name, boolean op) {
            this.name = name;
            this.op = op;
        }

        public EntityType getType() {
            return EntityType.PLAYER;
        }

        public String getName() {
            return name;
        }

        public Player getKiller() {
            return killer;
        }

        public double getHealth() {
            return 20.0;
        }

        public UUID getUniqueId() {
            return id;
        }

        public World getWorld() {
            return world;
        }

        public Location getLocation() {
            return new FakeLocation(world, biome);
        }

        public PlayerInventory getInventory() {
            return inv;
        }

        public boolean isOp() {
            return op;
        }

        public void sendMessage(String message) {
            sent.add(message);
        }
    }

    static class FakeRecipe implements Recipe {
        final ItemStack result;

        FakeRecipe(ItemStack result) {
            this.result = result;
        }

        public ItemStack getResult() {
            return result;
        }
    }

    static class FakeCraftInv implements CraftingInventory {
        final ItemStack[] matrix;

        FakeCraftInv(ItemStack[] matrix) {
            this.matrix = matrix;
        }

        public ItemStack[] getMatrix() {
            return matrix;
        }
    }

    static class FakeCraftEvent extends CraftItemEvent {
        final FakePlayer who;
        final Recipe recipe;
        final CraftingInventory inv;
        final boolean shift;

        FakeCraftEvent(FakePlayer who, Recipe recipe, boolean shift) {
            this.who = who;
            this.recipe = recipe;
            this.shift = shift;
            this.inv = new FakeCraftInv(new ItemStack[]{new FakeStack(Material.DIRT, 64)});
        }

        @Override
        public Recipe getRecipe() {
            return recipe;
        }

        @Override
        public CraftingInventory getInventory() {
            return inv;
        }

        @Override
        public boolean isShiftClick() {
            return shift;
        }

        @Override
        public HumanEntity getWhoClicked() {
            return who;
        }
    }

    static class FakeMob implements Ageable {
        final EntityType type;
        final boolean adult;
        final Player killer;

        FakeMob(EntityType type, boolean adult, Player killer) {
            this.type = type;
            this.adult = adult;
            this.killer = killer;
        }

        public EntityType getType() {
            return type;
        }

        public String getName() {
            return type.name();
        }

        public Player getKiller() {
            return killer;
        }

        public double getHealth() {
            return 0;
        }

        public boolean isAdult() {
            return adult;
        }
    }

    static class FakeDamage extends DamageSource {
        final Entity causing;

        FakeDamage(Entity causing) {
            this.causing = causing;
        }

        @Override
        public Entity getCausingEntity() {
            return causing;
        }
    }

    static class FakeEntityDeath extends EntityDeathEvent {
        final LivingEntity dead;
        final DamageSource src;

        FakeEntityDeath(LivingEntity dead, DamageSource src) {
            this.dead = dead;
            this.src = src;
        }

        @Override
        public LivingEntity getEntity() {
            return dead;
        }

        @Override
        public DamageSource getDamageSource() {
            return src;
        }
    }

    static class FakePlayerDeath extends PlayerDeathEvent {
        final FakePlayer victim;

        FakePlayerDeath(FakePlayer victim) {
            this.victim = victim;
        }

        @Override
        public Player getEntity() {
            return victim;
        }
    }

    static class FakeWorldChange extends PlayerChangedWorldEvent {
        final FakePlayer who;
        final World from;

        FakeWorldChange(FakePlayer who, World from) {
            this.who = who;
            this.from = from;
        }

        @Override
        public Player getPlayer() {
            return who;
        }

        @Override
        public World getFrom() {
            return from;
        }
    }

    static class FakeChat extends AsyncChatEvent {
        final FakePlayer who;
        boolean cancelled;

        FakeChat(FakePlayer who) {
            this.who = who;
        }

        @Override
        public Player getPlayer() {
            return who;
        }

        @Override
        public Component message() {
            return Component.text("x", NamedTextColor.AQUA);
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public void setCancelled(boolean c) {
            cancelled = c;
        }
    }

    static class CannedGateway extends AiGateway {
        String reply = "canned roast";

        CannedGateway() {
            super(new KeyStore(new byte[32]), Map.of(), Map.of(), "m",
                    0.0, 10, 1, RotationMode.ROUND_ROBIN, List.of(), 5);
        }

        @Override
        public CompletionResult complete(String system, String user, int timeoutSeconds) {
            return new CompletionResult(true, reply, "canned", false, "");
        }
    }

    // ---------------- rig ----------------

    static class Rig {
        final BotConfig cfg = new BotConfig(new FileConfiguration());
        final CooldownManager cooldowns = new CooldownManager();
        final PromptBuilder.OptOuts optOuts = new PromptBuilder.OptOuts();
        final RoastDiary diary = new RoastDiary(10);
        final CannedGateway gateway = new CannedGateway();
        final JavaPlugin plugin = new JavaPlugin() {
        };
        final ChatDispatcher chat = new ChatDispatcher(plugin, "TestBot");
        final RoastDispatcher dispatcher = new RoastDispatcher(chat, gateway, diary,
                new ResponseValidator(200, 0.8, List.of()), cooldowns,
                "sys {BOTNAME}", "TestBot", cfg.perPlayerCooldownMs,
                cfg.globalCooldownMs, 5);
        final KeyStore keys;
        final Map<String, AiProvider> providers = new LinkedHashMap<>();

        Rig() throws Exception {
            keys = new KeyStore(new byte[32]);
            for (AiProvider p : Providers.defaults()) {
                providers.put(p.id(), p);
            }
        }

        ChatListener.AdminActions admin() {
            return new ChatListener.AdminActions() {
                public String addKey(String providerId, String rawKey) {
                    return ChatListener.doAddKey(keys, providers, providerId, rawKey);
                }

                public String setKeyParty(String baseUrl, String rawKey) {
                    return "ok";
                }

                public String setRotation(String mode) {
                    return "ok";
                }

                public String rotationStatus() {
                    return "ok";
                }

                public String setName(String name) {
                    return "ok";
                }

                public String status() {
                    return "ok";
                }
            };
        }

        ChatListener chatListener() {
            return new ChatListener(cfg, optOuts, dispatcher, cooldowns, admin(), "TestBot");
        }
    }

    static void resetStage() {
        Bukkit.broadcastLog.clear();
        PlainTextComponentSerializer.next = "";
    }

    // ---------------- tests ----------------

    public static void main(String[] args) throws Exception {
        Bukkit.scheduler = new InlineScheduler();

        // 1. craft threshold fires
        {
            Rig r = new Rig();
            resetStage();
            CraftListener l = new CraftListener(r.cfg, new CraftCounter(r.cfg.windowMs),
                    r.cooldowns, r.optOuts, r.dispatcher);
            FakePlayer p = new FakePlayer("Steve", false);
            for (int i = 0; i < 6; i++) {
                l.onCraft(new FakeCraftEvent(p, new FakeRecipe(new FakeStack(Material.TNT, 1)), false));
            }
            check(Bukkit.broadcastLog.size() == 1, "craft-fires-once");
            check(r.diary.recent(p.getUniqueId()).size() == 1, "craft-diary");
            check(Bukkit.broadcastLog.get(0).toString().contains("canned roast"),
                    "craft-content");
        }

        // 2. below threshold stays silent
        {
            Rig r = new Rig();
            resetStage();
            CraftListener l = new CraftListener(r.cfg, new CraftCounter(r.cfg.windowMs),
                    r.cooldowns, r.optOuts, r.dispatcher);
            FakePlayer p = new FakePlayer("Alex", false);
            l.onCraft(new FakeCraftEvent(p, new FakeRecipe(new FakeStack(Material.TNT, 2)), false));
            check(Bukkit.broadcastLog.isEmpty(), "craft-below-silent");
        }

        // 3. opt-out respected, then opt back in
        {
            Rig r = new Rig();
            resetStage();
            ChatListener cl = r.chatListener();
            FakePlayer p = new FakePlayer("Bob", false);
            PlainTextComponentSerializer.next = "!optout";
            FakeChat c1 = new FakeChat(p);
            cl.onChat(c1);
            check(c1.isCancelled() && r.optOuts.isOptedOut(p.getUniqueId()), "optout-toggle");
            CraftListener l = new CraftListener(r.cfg, new CraftCounter(r.cfg.windowMs),
                    r.cooldowns, r.optOuts, r.dispatcher);
            for (int i = 0; i < 6; i++) {
                l.onCraft(new FakeCraftEvent(p, new FakeRecipe(new FakeStack(Material.TNT, 1)), false));
            }
            check(Bukkit.broadcastLog.isEmpty(), "optout-silences-craft");
            PlainTextComponentSerializer.next = "!optout";
            cl.onChat(new FakeChat(p));
            check(!r.optOuts.isOptedOut(p.getUniqueId()), "optin-toggle");
        }

        // 4. mention roasts
        {
            Rig r = new Rig();
            resetStage();
            FakePlayer p = new FakePlayer("Sarah", false);
            PlainTextComponentSerializer.next = "@TestBot u look dumb";
            FakeChat c = new FakeChat(p);
            r.chatListener().onChat(c);
            check(!c.isCancelled() && Bukkit.broadcastLog.size() == 1, "mention-roasts");
        }

        // 5. non-OP keyadd ignored
        {
            Rig r = new Rig();
            resetStage();
            FakePlayer p = new FakePlayer("Griefer", false);
            PlainTextComponentSerializer.next = "!keyadd openai sk-1234567890";
            FakeChat c = new FakeChat(p);
            r.chatListener().onChat(c);
            check(c.isCancelled() && p.sent.isEmpty() && r.keys.enabledCount("openai") == 0,
                    "keyadd-nonop-silent");
        }

        // 6. OP keyadd works, short key rejected
        {
            Rig r = new Rig();
            resetStage();
            FakePlayer op = new FakePlayer("Admin", true);
            PlainTextComponentSerializer.next = "!keyadd openai sk-1234567890abcdef";
            r.chatListener().onChat(new FakeChat(op));
            check(r.keys.enabledCount("openai") == 1
                    && op.sent.get(0).contains("Key added"), "keyadd-op-ok");
            PlainTextComponentSerializer.next = "!keyadd openai short";
            r.chatListener().onChat(new FakeChat(op));
            check(r.keys.enabledCount("openai") == 1, "keyadd-short-rejected");
        }

        // 7. villager mass-murder -> bounty + ALL CAPS
        {
            Rig r = new Rig();
            resetStage();
            BountyManager bounties = new BountyManager();
            KillListener l = new KillListener(r.cfg, new KillTracker(r.cfg.windowMs), bounties,
                    r.cooldowns, r.optOuts, r.dispatcher);
            FakePlayer killer = new FakePlayer("Murderer", false);
            for (int i = 0; i < 3; i++) {
                l.onDeath(new FakeEntityDeath(
                        new FakeMob(EntityType.VILLAGER, true, killer), new FakeDamage(null)));
            }
            check(bounties.hasBounty(killer.getUniqueId()), "murder-bounty-set");
            List<String> roasts = r.diary.recent(killer.getUniqueId());
            check(roasts.size() == 1 && roasts.get(0).equals("CANNED ROAST"),
                    "murder-allcaps");
        }

        // 8. bounty claim on PvP kill
        {
            Rig r = new Rig();
            resetStage();
            BountyManager bounties = new BountyManager();
            DeathListener l = new DeathListener(r.cfg, bounties, r.cooldowns, r.optOuts,
                    r.dispatcher);
            FakePlayer victim = new FakePlayer("Wanted", false);
            FakePlayer hero = new FakePlayer("Hero", false);
            victim.killer = hero;
            bounties.setBounty(victim.getUniqueId(), "Wanted", 10000);
            l.onDeath(new FakePlayerDeath(victim));
            check(!bounties.hasBounty(victim.getUniqueId())
                    && Bukkit.broadcastLog.size() == 1, "bounty-claim");
        }

        // 9. nether entry roasts
        {
            Rig r = new Rig();
            resetStage();
            WorldListener l = new WorldListener(r.cfg, r.cooldowns, r.optOuts, r.dispatcher);
            FakePlayer p = new FakePlayer("Explorer", false);
            p.world = new FakeWorld(World.Environment.NETHER);
            l.onWorldChange(new FakeWorldChange(p, new FakeWorld(World.Environment.NORMAL)));
            check(Bukkit.broadcastLog.size() == 1, "dimension-roast");
        }

        // 10. global-cooldown backpressure sends milk, not AI
        {
            Rig r = new Rig();
            resetStage();
            r.gateway.reply = "second roast";
            FakePlayer p1 = new FakePlayer("P1", false);
            FakePlayer p2 = new FakePlayer("P2", false);
            r.dispatcher.roast(p1.getUniqueId(), "P1",
                    com.steelquill.mcroastbot.core.EventType.MENTION, "hi", "PLAYER:\nname: P1",
                    r.cfg.milkCooldownMs);
            check(Bukkit.broadcastLog.size() == 1, "milk-first-roast");
            r.dispatcher.roast(p2.getUniqueId(), "P2",
                    com.steelquill.mcroastbot.core.EventType.MENTION, "hi", "PLAYER:\nname: P2",
                    r.cfg.milkCooldownMs);
            String milk = Bukkit.broadcastLog.get(1).toString();
            check(Bukkit.broadcastLog.size() == 2
                    && (milk.contains("milk") || milk.contains("mum")), "milk-backpressure");
        }

        System.out.println("\nPASS=" + pass + " FAIL=" + fail);
        if (fail > 0) {
            System.exit(1);
        }
    }
}
