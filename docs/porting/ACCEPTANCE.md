# NeoForge port acceptance status

Unchecked = NOT demonstrated by source presence. Do not publish the probe.

## M0: toolchain and API (current)
- [x] Java 21 + ModDevGradle build and JUnit 5 on CI (Actions run 45004250302)
- [x] Bootstrapping in clean NeoForge 1.21.1 dedicated server with TLM (Actions 45004595146)
- [ ] TLM recognizes external ILittleMaid extension in running dedicated server
- [x] 1.20.1 Java/resource sourceSets excluded from probe build (static boundary + CI)
- [ ] Data Components does not lose foreign fields (unit + native tests)

## M1: storage
- [ ] Durable intent fsync/atomic replace and restart quarantine on current head
- [ ] Typed channel persistent and stream codecs, duplicate/mixed identity rejection; full recall/switch pending
- [ ] Native reference persistent/stream codec and foreign components verified on current head
- [ ] Native maid binding attachment verified through TLM serialization
- [ ] World snapshot and ticket state reload together; stale ticket rejected after recapture
- [ ] RCON command acknowledgment and both native markers on current head
- [ ] Durable crash journal, aggregate storage limits and reconciliation across entity/player/world files
- [ ] Full owned-key inventory for all weapons, channels and archives
- [ ] v1 snapshot round-trip on native TLM entity (CI pending); old 1.20.1 golden fixtures still required
- [ ] New/old SavedData world schema upgrade and identity
- [ ] Invalid data or unsupported format remains untouched
- [ ] Carrier storage preserves all 1.21.1 non-CUSTOM_DATA components

## M2: lifecycle
- [ ] Production capture / manifest / recall, same UUID, no loss (isolated developer-only trial pending CI)
- [ ] Model, inventory, equipment, task, bauble, favorability conserved
- [ ] Duplicate item cannot create duplicate live maid
- [ ] Spawn cancellation retains the weapon snapshot
- [ ] Cross-dimension unloaded maid cannot be duplicated
- [ ] Soul talisman, broken sword, emergency recovery
- [ ] Unauthorized clients cannot mutate contracts

## M3: feature parity
- [ ] Native resonance unit + server integration on current head (payment, owner, duplicate attempt, live/stored balance continuity)
- [ ] Native resonance tick, progression, config, oath/waiver, events, HUD and old pool migration
- [ ] Resonance, oath, projection, mastery
- [ ] White Fox, Black Fox and Akatsuki
- [ ] Manka and independent ability systems
- [ ] Interiors, shrines, recipes, models, language and rendering
- [ ] Optional mods tested or safely unsupported

## M4: release
- [ ] Dedicated server startup and clean shutdown
- [ ] Real client screenshot / gameplay evidence
- [ ] Multiplayer ownership and concurrent combat
- [ ] Upgrade copied 1.20.1 world; no lost items/maids
- [ ] Replace probe ID with stable maid_weapon, preserve registry names
- [ ] Published jar free of test/probe/old-world incompatible classes

## Historical verified baseline
Commit 65e610c passed both compile/JUnit and native TLM server smoke in Actions run 45411250887. This verifies the previous single-reference lifecycle, native component persistence, stale-ticket rejection and resonance continuity, not the new journal/channel changes or real-world restart safety.
