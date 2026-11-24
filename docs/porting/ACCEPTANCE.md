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

## Long-term maintenance increment (unverified)
- [ ] Java 17 shared compile gate, shared test source set, selective synchronization
- [ ] Unified skill executor and native probe payment/cooldown
- [ ] Native status payload codec, registration and actual client roundtrip (client test still required)
- [ ] Persistent spirit vein progression and selected stored carrier tick recovery (client/player test still required)
- [ ] Recovery inspection; no mutation, cross-session reconciliation still unimplemented

## Follow-up increment
- [ ] Forge draft PR #39 shared policy integration; native Forge CI required.
- [ ] Prior MANIFEST / committed LIVE operator acknowledgement, no spawn; STORED and pending restart recovery remain blocked.
- [ ] Sealed channel shared resonance and cooldown, mixed/stale denial; live recall/creation/manifestation still unavailable.
- [ ] Probe spirit vein upgrade items consume only on sequential success; actual player test required, production recipes and migration pending.

## Selected-channel and gameplay migration
- [ ] Native two-channel manifest/recall/selection, separate inventory and pool continuity, live lease and stale ticket rejection (required smoke marker).
- [ ] Client HUD in actual multiplayer, 26-byte authorized status, ticket correlation and expiry.
- [ ] Inventory/menu independent recovery including dormant and loaded live channels, duplicate deduplication; actual player tick test.
- [ ] Mastery Lv1-10 defaults, persistent progression and direct verified damage/death attribution; actual combat test; optional indirect attribution pending.
- [ ] Native preset origin registration, creation/transfer, ordinary restart and crash recovery, full production registry and spirit gameplay still release blockers.

## Oath, baubles and checkpoint increment
- [ ] Real native TLM ultimate bauble slot registration, stored/live waiver, burn bypass and ticket-cache rotation; required smoke marker.
- [ ] Oath O-key activation, one live companion, 2.5%/second burn, interruption/recall, per-maid online rest and backlash. Actual player/client combat test required; cooperation/effect/config and recovery gaps remain.
- [ ] Actual clean stop and second boot of the same disposable world. Reload persisted item and ledger, 29 diamonds, 53 XP, 863 resonance, manifest/recall and stale-ticket rejection; required smoke marker. No unclean power-loss recovery claim.
