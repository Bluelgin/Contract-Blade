# Contract Blade — NeoForge 1.21.1 port

> **IN PROGRESS / NOT FOR PLAY.** The current branch builds an isolated
> platform/API probe, **not** a replacement for the 1.20.1 Forge mod.

## Branch and toolchain contract

- 1.20.1 Forge source of truth: master, Java 17, ForgeGradle, unmodified.
- Long-lived NeoForge target: 1.21.1-neoforge, Java 21, NeoForge 21.1.x.
- Integration PR head: port/1.21.1-neoforge; PR base: 1.21.1-neoforge.
- Base commit: d1429a46492783c4e773d6ccb17b606ca48f5113.
- Upstream requirement: TLM 1.5.3 NeoForge for 1.21.1.
- Do not merge any NeoForge migration directly into master.

## Current development slice

The source of the existing mod remains untouched under src/main/java
and src/main/resources. The NEW Gradle build selects only src/port/java and
src/port/resources. This deliberately preserves the old code for comparison
while compiling a small modern NeoForge + TLM API probe.

The probe has its own temporary mod id: maid_weapon_port_probe.
It must NEVER be distributed as a gameplay mod or used to upgrade a world.
No legacy registries, contracts, skills or other feature parity is claimed.

Implemented exploration:
- A version-specific Data Components CUSTOM_DATA reader/writer skeleton.
- An explicit whitelist of known contract root keys, foreign-key preservation.
- A bounded, CRC-checked legacy GZIP envelope verifier (not entity deserialization).
- A compile-time probe for TLM ILittleMaid/LittleMaidExtension/BaubleManager.
- Java 21 build, CI definition, unit tests and legacy blocker inventory.

## Commands

    python3 tools/porting/check_probe.py
    python3 tools/porting/audit_legacy.py
    ./gradlew --no-daemon build
    ./gradlew --no-daemon runServer

Full TLM + resource runtime testing is a separate mandatory gate.

## Build migration phases

M0: establish toolchain and TLM API, integrity of legacy envelope.
M1: full registry parity, all stored contract keys, true old NBT codec,
    world SavedData, datafixes and failure semantics.
M2: TLM capture/manifest/recall/soul-talisman, identity ledger and equipment.
M3: Resonance, oath, black/white fox, Akatsuki, Manka, interiors and client.
M4: Optional integrations, dedicated server, client, multiplayer, golden worlds.
M5: Restore maid_weapon mod ID only after full parity and data safety passes;
    publish beta with explicit save migration status.

See SAVE_COMPATIBILITY.md, ACCEPTANCE.md and MAINTENANCE.md.
