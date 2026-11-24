# 1.20.1 -> 1.21.1 parity and architecture migration

Baseline master d1429a46492783c4e773d6ccb17b606ca48f5113: 556 Java sources, 196 resources, 49 test Java files. Before this increment, isolated probe 4fff20e: 20 Java sources, 5 resources, 8 test Java files. Retained old src/main does not compile in NeoForge. Counts do not measure feature parity.

| Domain | Forge implementation | Native approach/status |
|---|---|---|
| Platform | Forge Java 17, SimpleChannel, ForgeConfigSpec | Java 21 NeoForge probe; payload/config adapters pending |
| Lifecycle | ContractCarrierData, ContractLifecycleService, TLM storage, recovery journal | Native reference/attachment/store trial; durable journal, unloaded presence, broken/lost carrier, rebirth, emergency recovery pending |
| Channels | external/intrinsic, embedded spirit, transfer | Legacy read exploration only; native channel model and transfer authorization pending |
| Resonance | world pools, activity, payment attempts, progression | Native per-maid pool, authorization/payment service and pure rules implemented; active ticking, config, oath/waiver, progression, events, HUD pending |
| Mastery and abilities | mastery SavedData, ability registry/reload, kill credit, milestones | Legacy storage probe only; typed progression and reload pending |
| Deployment | companion, projection equipment, combat tasks, work, home safety | pending; preserve TLM task/equipment contracts |
| Oath | burn/rest/service/feedback | pending; independent per-contract session and server-owned costs |
| White/black fox | blessings, guard, core, boss, arena, challenge | pending; domain rules + native entities/AI/render |
| Akatsuki and soul | domain, anchors, observation, damage budget, rescue | pending; native damage pipeline adapters |
| Manka | waves, projectiles, guard, wheel, metrics | pending; bounded sessions + native payloads |
| Interior | dimension, terrain, residents, activities, furniture | pending; chunk-safe resident state |
| Optional mods | SlashBlade, Blade Tetra, Epic Fight, TaCZ, Goety, Iron's etc. | pending; verify exact dependency versions; capability-based adapters |
| Assets/registries | items, blocks, menus, recipes, models, sounds, language, shrine | pending; preserve stable IDs only at production cutover; audit formats |
| Migration | legacy NBT and ForgeCaps | pending; explicit backup and genuine fixtures |

## Architecture rules
Pure domain rules independent of Minecraft and storage. Preserve actual 1.20.1 behavior first, not historical design notes. Use native typed item components, entity attachments, world authority and separately persisted high-frequency progression. Do not copy all legacy classes wholesale. Revalidate authority on every execution; clients cannot provide cost/waiver or identity authority. Unknown schemas fail closed. Never sync complete maid snapshots to clients.

The current resonance defaults reflect master: pool 1000 / legacy 200; rates in legacy units per second stored .5, manifested idle 4, ability idle .5, stored combat 0, manifested combat 2; conversion multiplies by 5. No automatic recovery is enabled yet. Transfer/rebinding must explicitly migrate authorized accounts in future; no silent reset.

## Release gates
P0: current-head CI, durable journal across world/entity/player saves, unloaded entity presence, migration and recovery fixtures. P1: gameplay parity, native config/payloads, dependency gates. P2: performance, client/multiplayer, real-world acceptance. Probe namespace must not be released.
