# McRoastBot

Chat-only AI roast bot for Paper 1.21. Deterministic detectors decide what is
interesting; the AI only roasts. No body, no OP, no grief - just cyan chat.

## Architecture

```
event -> detector (cheap, sync) -> threshold? -> cooldowns? -> AI (async)
  -> validate -> diary -> cyan chat
```

- `config/` - typed `config.yml` view
- `core/` - `CooldownManager`, `RoastDiary` (last 10/player), `ResponseValidator`,
  `Observation`, `PromptBuilder`, `EventType` (all Bukkit-free, unit-tested)
- `ai/` - 10 providers + KeyParty, AES-256-GCM `KeyStore`, rotation/failover
  `AiGateway`, zero-dependency JSON `ChatPayload`
- `detect/` - `CraftCounter`, `KillTracker`, `BountyManager`
- `observe/` - sync `ObserveGatherer` (6x4x6 scan, entity radius) + `RandomObserver`
- `chat/` - `ChatDispatcher` (cyan), `RoastDispatcher` (single AI funnel), `ChatListener`
- `listeners/` - craft / kill / dimension / bounty-claim detectors
- `commands/` - `/optout`

## Build (Lightning AI box - needs network)

```
sudo apt install openjdk-21-jdk
# Gradle 8.10+ (sdkman or manual install)
gradle build
# jar -> plugins/McRoastBot-0.1.0.jar
```

Check https://papermc.io/downloads/paper first and bump `paperApiVersion` in
`gradle.properties` to your exact 1.21.x build if needed. This sandbox cannot
reach Paper's repo, so it was verified offline instead - see
`tools/offline-verify/README.md` (47/47 self-tests green; risky API signatures
cross-checked against PaperMC/Paper GitHub source).

## Server setup

1. Paper 1.21.x server, Java 21. Drop the jar in `plugins/`.
2. Optional: ViaVersion + ViaBackwards (old/new Java clients), Geyser +
   Floodgate (Bedrock, Phase 2 - untested, see roadmap in spec).
3. Start once to generate `config.yml`, then OP-only in-game setup:
   - `!keyadd nvidia <key>` (also `groq`, `openai`, `gemini`, `deepseek`,
     `together`, `openrouter`, `fireworks`, `mistral`)
   - `!apikeyparty <base-url> <key>` (KeyParty gateway, OpenAI-compatible)
   - `!keyrotation status` / `!keyrotation FAILOVER_PREFERRED`
   - `!changename <name>` - bot replies to `@<name>`
   - `!aistatus` - masked key counts (full keys are NEVER shown anywhere)
4. Players: `/optout` or `!optout` to be left alone (covers mentions too).

## Behavior

- Mass-craft (TNT>5, boats>10, armor stands>12 per window) gets roasted.
- Villager/cute/baby mass-kills trigger an ALL-CAPS murder bounty (10k
  doubloons); killing the bounty target pays out the joke.
- Nether/End/rare-biome entries, `@bot` mentions, and a 2-5 min random
  one-player-one-parameter check keep chat alive. Boring checks stay silent
  (`NO_ROAST`), rate-limit backpressure says "imma go get some milk brb".
- Per-player + global + per-event cooldowns; 10-roast diary per player;
  validator strips markdown/links, blocks slurs/threats/self-harm/PII/fake
  physicality claims, and rejects repeats.

## Security model

Keys are AES-256-GCM encrypted (per-server `.kek`, file perms 600), OP-only
`!`-commands (checked before parsing), 401 disables a key until re-added,
429/402/5xx rotate. No view/export command exists by design. The bot has no
permissions beyond broadcasting chat.

## More-feature ideas (fits the concept, not yet built)

Death roasts, first-join welcome roast (opt-in), advancement/fishing-fail
roasts, ugly-build judge (block-pattern heuristics), playtime milestones,
"most roasted" leaderboard, per-player roast-intensity preference.
