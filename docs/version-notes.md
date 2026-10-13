# Version research notes

## Target: Paper 1.21.x, Java 21

- `api-version: '1.21'` in `paper-plugin.yml`; dependency
  `io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT` (see `gradle.properties`).
- Before building on the Lightning box, check https://papermc.io/downloads/paper
  for the newest 1.21.x build and bump `paperApiVersion` if needed.
- Note: you mentioned latest upstream is 26.4. This sandbox cannot reach
  papermc.io to confirm, so 1.21 is the pinned target per your choice. If the
  Paper build train has moved (e.g. 26.x), bump `paperApiVersion` and
  `api-version` together and re-check the API list below.

## "All versions" honesty

One jar cannot natively speak every protocol version. The compatibility story is:

- **Server side:** this plugin compiles against ONE Paper API (1.21.x).
- **Old/new Java clients:** ViaVersion + ViaBackwards on the server translate
  protocol. Our plugin uses only high-level Bukkit/Paper APIs (chat, events,
  inventories, scheduler), which are protocol-agnostic, so Via compatibility
  comes for free. Declared as softdepends; not required at runtime.
- **Bedrock clients:** Phase 2 via Geyser + Floodgate (see main README).
  UUID-based identity is used everywhere so Floodgate-prefixed names don't
  break opt-outs, diaries, or bounties.

## APIs used (verified against PaperMC/Paper GitHub source)

| API | Status |
|---|---|
| `io.papermc.paper.event.player.AsyncChatEvent` (`message()`, `setCancelled()`, `getPlayer()`) | verified in source |
| `CraftItemEvent.getRecipe()/getInventory()`, `isShiftClick()` | verified in source |
| `EntityDeathEvent.getEntity()/getDamageSource()`, `LivingEntity.getKiller()`, `DamageSource.getCausingEntity()` | verified in source |
| `PlayerChangedWorldEvent.getFrom()` | verified in source |
| `Bukkit.broadcast(Component)` (Adventure, cyan) | verified in source |
| `Material.TNT/OAK_BOAT/ARMOR_STAND`, `EntityType` cute set, `Ageable.isAdult()` | verified present |
| `World.getNearbyEntities(Location,double,double,double)`, `Block.getBiome()`, `World.Environment` | verified in source |
| `paper-plugin.yml` legacy keys (`softdepend`, `commands`, `load: POSTWORLD`) | per Paper `PluginMeta` docs |
| `PlayerTeleportEvent.getTo()`, `PlayerDeathEvent.getEntity()->Player`, scheduler, `YamlConfiguration` | long-stable, not re-verified |

## Deliberately NOT used in MVP

- `World.locateNearestStructure(...)` (signature changed across versions;
  structure detection is a documented TODO, biome/dimension ship now).
- Baritone / movement / NPC APIs (physical agent is Phase 2+, see
  `docs/physical-agent-notes.md`).
