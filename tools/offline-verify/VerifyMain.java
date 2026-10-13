package verify;

import com.steelquill.mcroastbot.ai.ChatPayload;
import com.steelquill.mcroastbot.ai.AiProvider;
import com.steelquill.mcroastbot.ai.KeyStore;
import com.steelquill.mcroastbot.ai.Providers;
import com.steelquill.mcroastbot.chat.ChatListener;
import com.steelquill.mcroastbot.core.CooldownManager;
import com.steelquill.mcroastbot.core.EventType;
import com.steelquill.mcroastbot.core.Observation;
import com.steelquill.mcroastbot.core.PromptBuilder;
import com.steelquill.mcroastbot.core.ResponseValidator;
import com.steelquill.mcroastbot.core.RoastDiary;
import com.steelquill.mcroastbot.detect.BountyManager;
import com.steelquill.mcroastbot.detect.CraftCounter;
import com.steelquill.mcroastbot.detect.KillTracker;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Offline self-test for all Bukkit-free logic. Run with plain `java`. */
public final class VerifyMain {
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

    public static void main(String[] args) throws Exception {
        // CooldownManager
        CooldownManager cd = new CooldownManager();
        check(cd.tryAcquire("a", 1000, 0), "cd-first-acquire");
        check(!cd.tryAcquire("a", 1000, 500), "cd-blocked");
        check(cd.tryAcquire("a", 1000, 1001), "cd-expired");
        check(cd.remainingMs("a", 1000, 1500) == 501, "cd-remaining");
        cd.reset("a");
        check(cd.tryAcquire("a", 1000, 1500), "cd-reset");

        // RoastDiary
        RoastDiary diary = new RoastDiary(10);
        UUID steve = UUID.randomUUID();
        for (int i = 0; i < 12; i++) {
            diary.add(steve, "roast" + i);
        }
        List<String> recent = diary.recent(steve);
        check(recent.size() == 10 && recent.get(0).equals("roast2")
                && recent.get(9).equals("roast11"), "diary-cap-10-fifo");
        Map<String, List<String>> snap = diary.snapshot();
        RoastDiary d2 = new RoastDiary(10);
        snap.put("not-a-uuid", List.of("x"));
        d2.restore(snap);
        check(d2.recent(steve).size() == 10, "diary-restore-skips-bad-uuid");

        // ResponseValidator
        ResponseValidator v = new ResponseValidator(200, 0.8, List.of());
        check(v.validate("bro brought dirt to a diamond fight", List.of(), false).ok(),
                "val-accept");
        check(!v.validate("NO_ROAST", List.of(), false).ok(), "val-no-roast-silent");
        check(!v.validate("go kill yourself noob", List.of(), false).ok(), "val-block-selfharm");
        check(!v.validate("join my server https://evil.example", List.of(), false).ok(),
                "val-block-link");
        check(!v.validate("i teleported to you just now", List.of(), false).ok(),
                "val-block-fake-physical");
        check(v.validate("bro brought dirt to a diamond fight", List.of(), true).text()
                        .equals("BRO BROUGHT DIRT TO A DIAMOND FIGHT"), "val-murder-caps");
        String dup = "you have fourteen boats and zero wins";
        check(!v.validate(dup, List.of(dup), false).ok(), "val-similarity-reject");
        String long300 = "w".repeat(1) + "ord ".repeat(60);
        var trunc = v.validate(long300, List.of(), false);
        check(trunc.ok() && trunc.text().length() <= 200, "val-truncate");
        String md = v.validate("**bold** `code` # tag > quote", List.of(), false).text();
        check(!md.contains("*") && !md.contains("`") && !md.contains("#"), "val-strip-markdown");

        // ChatPayload
        String req = ChatPayload.buildRequest("m", "say \"hi\"\nnewline", "user\\x", 0.9, 120);
        check(req.contains("\\\"hi\\\"") && req.contains("\\n") && req.contains("user\\\\x"),
                "payload-escape");
        String sample = "{\"choices\":[{\"message\":{\"content\":\"heyo \\u0041\\n slash\\\\ end\"}}]}";
        check("heyo A\n slash\\ end".equals(ChatPayload.extractContent(sample)),
                "payload-extract");
        check(ChatPayload.extractContent("{}") == null, "payload-extract-absent");
        check(ChatPayload.classify(200) == ChatPayload.Outcome.OK, "classify-200");
        check(ChatPayload.classify(401) == ChatPayload.Outcome.DISABLE_KEY, "classify-401");
        check(ChatPayload.classify(429) == ChatPayload.Outcome.ROTATE_NEXT, "classify-429");
        check(ChatPayload.classify(500) == ChatPayload.Outcome.ROTATE_NEXT, "classify-500");
        check(ChatPayload.classify(400) == ChatPayload.Outcome.FATAL, "classify-400");

        // KeyStore AES roundtrip + rotation
        byte[] kek = new byte[32];
        for (int i = 0; i < 32; i++) {
            kek[i] = (byte) i;
        }
        KeyStore ks = new KeyStore(kek);
        String id1 = ks.addKey("nvidia", "nv-secret-12345678");
        String id2 = ks.addKey("nvidia", "nv-second-12345678");
        check(ks.decrypt(ks.snapshot().get(id1)).equals("nv-secret-12345678"), "keystore-roundtrip");
        var n1 = ks.next("nvidia");
        var n2 = ks.next("nvidia");
        check(n1 != null && n2 != null && !n1.id().equals(n2.id()), "keystore-roundrobin");
        ks.markInvalid(id1);
        check(ks.enabledCount("nvidia") == 1, "keystore-disable-401");
        check(KeyStore.mask("abcdef123456").equals("****3456"), "keystore-mask");
        check(ks.next("openai") == null, "keystore-empty-provider");

        // CraftCounter
        CraftCounter cc = new CraftCounter(600_000);
        UUID u = UUID.randomUUID();
        check(!cc.add(u, "tnt", 2, 5, 0), "craft-below");
        check(!cc.add(u, "tnt", 2, 5, 1), "craft-still-below");
        check(cc.add(u, "tnt", 2, 5, 2), "craft-crosses-once");
        check(!cc.add(u, "tnt", 1, 5, 3), "craft-reset-after-fire");
        check(!cc.add(u, "tnt", 4, 5, 700_000), "craft-window-expired");

        // KillTracker (window 1000ms: 3rd kill inside window fires)
        KillTracker kt = new KillTracker(1000);
        UUID k = UUID.randomUUID();
        check(!kt.add(k, KillTracker.Category.VILLAGER, 3, 0), "kill-1");
        check(!kt.add(k, KillTracker.Category.VILLAGER, 3, 10), "kill-2");
        check(kt.add(k, KillTracker.Category.VILLAGER, 3, 20), "kill-3-fires");
        check(!kt.add(k, KillTracker.Category.VILLAGER, 3, 30), "kill-reset-after-fire");

        // BountyManager
        BountyManager bm = new BountyManager();
        bm.setBounty(u, "Steve", 10000);
        check(bm.hasBounty(u), "bounty-set");
        var claimed = bm.remove(u);
        check(claimed != null && claimed.amount() == 10000 && !bm.hasBounty(u), "bounty-claim");

        // Observation compact format
        Observation ob = new Observation(u, "Steve");
        ob.dimension = "minecraft:overworld";
        ob.biome = "plains";
        ob.health = 18;
        ob.armor = "leather";
        ob.nearby.put("boats", 14);
        ob.flags.add("large_boat_cluster");
        String compact = ob.toCompact();
        check(compact.contains("PLAYER:") && compact.contains("SURROUNDINGS:")
                && compact.contains("INTERESTING:") && compact.contains("boats: 14"),
                "observation-compact");

        // PromptBuilder
        String sys = PromptBuilder.system("hello {BOTNAME}!", "RoastBot");
        check(sys.equals("hello RoastBot!"), "prompt-system");
        String ub = PromptBuilder.userBlock(EventType.RANDOM, "detail", compact,
                List.of("old roast"));
        check(ub.contains("EVENT_TYPE: RANDOM") && ub.contains("NO_ROAST")
                && ub.contains("RECENT ROAST DIARY"), "prompt-userblock");
        PromptBuilder.OptOuts oo = new PromptBuilder.OptOuts();
        check(oo.toggle(u) && oo.isOptedOut(u), "optout-toggle-on");
        check(!oo.toggle(u) && !oo.isOptedOut(u), "optout-toggle-off");
        oo.load(List.of(u.toString(), "junk"));
        check(oo.isOptedOut(u) && oo.save().size() == 1, "optout-load-save");

        // EventType caps
        check(EventType.MURDER.allCaps() && EventType.BOUNTY.allCaps()
                && !EventType.MENTION.allCaps(), "eventtype-caps");

        // Pollinations free provider
        Map<String, AiProvider> provs = new LinkedHashMap<>();
        for (AiProvider p : Providers.defaults()) {
            provs.put(p.id(), p);
        }
        AiProvider pol = provs.get("pollinations");
        check(pol != null && pol.keyless() && pol.openAiCompatible(), "pollinations-keyless");
        check(pol != null && pol.completionsUrl()
                .equals("https://text.pollinations.ai/openai/chat/completions"),
                "pollinations-url");
        String keylessMsg = ChatListener.doAddKey(ks, provs, "pollinations", "whatever");
        check(keylessMsg.contains("needs no key") && ks.enabledCount("pollinations") == 0,
                "pollinations-no-key-stored");

        System.out.println("\nPASS=" + pass + " FAIL=" + fail);
        if (fail > 0) {
            System.exit(1);
        }
    }
}
