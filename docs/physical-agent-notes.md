# Physical agent notes (Part B of the spec - NOT implemented in MVP)

The chat-only bot (MVP in `src/`) is the required foundation: gateway, key
rotation, cooldowns, diary, validation. A body comes later. This note records
the honest feasibility picture so Phase 2 starts from facts.

## Options for a server-side body (easiest first)

1. **Citizens NPC (recommended for prototype).** Mature API, no client mod,
   works on Paper, pathfinding via its navigator. Bot "speaks" through our
   existing `ChatDispatcher`. Crouch-look-follow are all doable. Downside:
   server must also install Citizens (a hard dependency for that phase).
2. **Packets-only fake entity (ArmorStand/player-type).** Full control, zero
   dependencies, but you re-implement tracking, pathfinding, and Bedrock
   translation bugs yourself. Only worth it if Citizens can't do something.
3. **Fake/bot player (e.g. bot-client accounts).** Real movement and
   interaction, but consumes a player slot, needs login handling, and looks
   like a normal player to anti-cheat and to Geyser. Most operational risk.
4. **Baritone-style pathfinding.** Baritone targets Forge/Fabric clients, NOT
   Paper servers. Do not plan on embedding Baritone in this plugin; take only
   the architectural idea (goals in, ticks out), as the spec already says.

## Architecture (unchanged from spec, still correct)

AI picks high-level intents (`FOLLOW_PLAYER`, `CROUCH_FOR_FRIENDSHIP`,
`INSPECT_CHEST`, `SPEAK`, `DO_NOTHING`); a deterministic controller executes
with pathfinding, timeouts, and cooldowns; results feed back as compact text.
Never let the model emit movement ticks, commands, or code.

## Hard requirements before any body ships

- Action allow-list + per-action cooldowns + OP-only toggles.
- Opt-out respected by physical targeting, not just chat.
- Failure handling: disconnects, chunk unloads, despawned items, timeouts.
- Bedrock testing: NPC visibility/metadata through Geyser is its own QA pass.
- Screenshots/vision need a RENDERING client; a Paper plugin cannot capture
  client frames. That stays a long-term research item, not a promise.

## Suggested phase order

P1 physical prototype (Citizens NPC + follow/look/crouch + chat) -> P2 event
observation reuse from MVP detectors -> P3 constrained action vocabulary ->
P4 random-interaction scheduler. Reuse `CooldownManager`, `RoastDiary`,
`AiGateway`, and `ResponseValidator` unchanged.
