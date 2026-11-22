# NeoForge port acceptance status

Unchecked = NOT demonstrated by source presence. Do not publish the probe.

## M0: toolchain and API (current)
- [ ] Java 21 + ModDevGradle build and JUnit 5 on CI
- [ ] Bootstrapping in clean NeoForge 1.21.1 dedicated server with TLM
- [ ] TLM recognizes external ILittleMaid extension
- [ ] All old Java and resource files excluded from probe JAR
- [ ] Data Components does not lose foreign fields (unit + native tests)

## M1: storage
- [ ] Full owned-key inventory for all weapons, channels and archives
- [ ] Byte-equivalent v1 snapshot round-trip on real TLM data
- [ ] New/old SavedData world schema upgrade and identity
- [ ] Invalid data or unsupported format remains untouched
- [ ] Carrier storage preserves all 1.21.1 non-CUSTOM_DATA components

## M2: lifecycle
- [ ] Capture / manifest / recall, same UUID, no loss
- [ ] Model, inventory, equipment, task, bauble, favorability conserved
- [ ] Duplicate item cannot create duplicate live maid
- [ ] Spawn cancellation retains the weapon snapshot
- [ ] Cross-dimension unloaded maid cannot be duplicated
- [ ] Soul talisman, broken sword, emergency recovery
- [ ] Unauthorized clients cannot mutate contracts

## M3: feature parity
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
