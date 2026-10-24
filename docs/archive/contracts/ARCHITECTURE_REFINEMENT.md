# Contract Blade architecture refinement

> 历史方案与实现记录，可能包含已撤回功能、旧数值和当时的测试结果。
> 当前说明从 [文档导航](../../README.md) 进入；本页不作为现行玩法指南。

Baseline: PR #15, `ef9847c473476213258734cfb92b10939d03c46b`,
`codex/simplify-contract-lifecycle`. This branch is stacked on that branch, not master.
No parent PR is merged, closed or rewritten. Gameplay parameters and saved NBT keys remain unchanged.

## A. Audit and minimal plan

| Finding and source | Cause / consequence | Decision |
|---|---|---|
| `compat/tlm/ContractMaidLifecycleService.captureBound` | Task/projection preparation and growth preparation outside a common rejection boundary; binding-only rollback; notification exceptions can escape after storage succeeds | Keep one sequential orchestration method; protect preparation, restore weapon/binding/task/schedule on failure; isolate post-commit observers |
| `ContractMaidLifecycleService.manifest` | One catch covers both unpublished candidates and published entities; runtime preparation throws before snapshot removal | Separate publication failures from completion; a published entity stays authoritative despite runtime/event errors |
| `compat/tlm/ContractMaidStorage.commit` | NBT auditing occurs after `setTag`, and could throw despite successful encoding | Keep atomic codec boundary; post-commit audit failure logs but does not report rejection |
| `system/fox/FoxSpiritAbilityAuthorization` | Old proof compares root object plus declared checksum/length; in-place bytes/legacy edits or world presence can bypass a cached proof | Cache only decoding of an immutable copied contract, scoped to server and owner; current residency/ledger/world checks always execute |
| `system/deployment/ContractCarrierLossService.process/resolve` | Boolean completion, callback inside rescue, UNKNOWN presence converted to true, ledger changed before fallible cleanup | Typed internal result; scheduling owns completion and notification; preserve UNKNOWN rather than fabricate presence |
| `system/contract/ContractAnomalyRecoveryService.record/cleanup` | `reason` and `record` no longer affect logic | Keep deprecated public signatures, use named `confirmedRemoved` and two-argument `cleanup` internally |
| `tools/validate_fox_models.py` | Old dual-fox version/list excludes an already documented cover, tail layer and Akatsuki page; loose added-file handling; mirrored notices lag the ZIP | Pin exact reviewed member inventory and hashes; keep original source SHA, animation invariants and license checks; synchronize existing credits/checksums without changing model ZIPs |
| `event/ContractRecoveryCommand` | Offline deletion bypasses entity events and can strand confirmed presence | Add an explicit permission-2 `confirm-absent <player> after-world-audit` correction; never infer death from timeout/unload |

No new universal validator, backup manager, rotating receipt, physical-copy lock or ability-specific
lifecycle service is introduced. Model assets themselves are unchanged. Known upstream Akatsuki
animation tracks refer to optional outfit/weapon bones absent from this rig; their exact reviewed
sets and resource bytes are pinned, not silently accepted as arbitrary unknown tracks.

## Core responsibility table

All Java paths below are relative to `src/main/java/com/maidweapon/forge/`.

| Module | Owns | Must not own |
|---|---|---|
| `system/contract/ContractLifecycleService` | Carrier-independent operation routing, owner/entry gate, transient transition scope | TLM task internals, ability growth effects |
| `compat/tlm/ContractMaidLifecycleService` | One real-entity ↔ snapshot operation, commit ordering, exception boundaries | Queue scheduling, backup discovery |
| `compat/tlm/TlmEntityAdapter` | Version-tolerant native entity/task access | Contract permissions, invented backup policy |
| `compat/tlm/ContractMaidStorage` | Compressed/legacy codec boundary and payload publication | Live entity deletion/spawning |
| `system/contract/ContractCarrierData` | Shared carrier metadata; legacy item forwarding remains | Particular weapon implementation |
| `compat/tlm/SoulTalismanService` | Normal slab destination and stable interior binding compatibility | Rotating tokens or clone embargo |
| `system/contract/ContractRecoveryJournal` | Current destination and confirmed presence; read-compatible legacy incidents | Independent permission based on old incidents |
| `system/contract/ContractAnomalyRecoveryService` | Reconcile current carrier/entity facts, preserve last confirmed removed snapshot | Custom backup storage/scanning |
| `system/deployment/ContractWeaponLocator` | Cross-dimensional UUID lookup, carried stack lookup, spawn collision gate | Snapshot decoding or permanent accident locks |
| `system/deployment/ContractRecoveryService` | Last known location, bounded chunk tickets, timeout diagnostics | Authorizing a new entity because a chunk recall timed out |
| `system/deployment/ContractCarrierLossService` / `ContractCarrierLossJournal` | Durability-confirmed, bounded, restart-compatible rescue scheduling and outcomes | Treating generic destruction/absence as death |
| `compat/tlm/ContractMaidRebirthBridge` | Thin native photo/film identity reconnection | Owning TLM backup consumption or directories |
| `system/fox/FoxSpiritState` / `FoxSpiritLedger` | Story/origin/token identity and current narrative destination | Second complete inventory/maid snapshot |
| `system/fox/FoxSpiritAbilityAuthorization` | Current stored narrative resident authorization, bounded decoded proof | Historical residency permission |
| `api/ability/*` / `system/mastery/*` | Generic registered features/providers and server-owned player mastery | SlashBlade-dependent progression authority |

## Actual call relationships

Before and after: interaction/table/deployment → `ContractLifecycleService` → existing thin
`TouhouLittleMaidHelper` compatibility facade → `ContractMaidLifecycleService` → adapter/storage.
This public facade is retained for addon compatibility. It is not expanded into another authority.
The architecture change is inside transaction boundaries and data semantics, not additional layers.

Before: `resolve` performed rescue, callbacks and messages; a boolean told `process` to dequeue.
After: `resolve` completes one recovery and returns a named result; `process` removes/marks the task
before calling observers. An observer cannot cause a completed film/release to be replayed.

Before: ability cached a permission proof using mutable NBT identity and partial declared metadata.
After: current world/ledger/residency checks precede every cache hit; only the exact copied snapshot
validation is reused for at most 20 ticks. Weak keys do not retain ItemStacks through value references.
Server identity, player UUID and the ledger's original maid UUID scope each proof. Existing server-stop
cleanup in `WhiteFoxBlessingService.clearSession` clears the cache. No world permission is cached.

## Capture transaction (actual sequence)

1. Entry routing checks carrier/owner/mod availability; transient transition is marked in a `finally` scope.
2. Adapter entry validates live owned maid, matching maid/binding and fox residency; rejects existing payload.
3. Save previous weapon NBT, destination, binding, native task and schedule. Interior location remembering
   now follows the binding gate rather than mutating location before a refusal.
4. Inside the preparation boundary: ensure binding, restore original native work settings, clear temporary
   optional projections, save full entity, check owner/UUID/binding, remove emergency-progress duplication
   from the saved ForgeData, prepare name/favorability/legacy growth.
5. `ContractMaidStorage.commit` encodes and validates a candidate tag before publishing it. On rejection or
   preparation exception restore weapon/binding/task/schedule; never discard the live maid.
6. In the existing successful order: destination stored, mastery observation, incident reconciliation,
   mark core transfer, discard entity, clear companion/task metadata, post captured event, optional message.
   Observer errors after storage publication are logged, and remaining required stages continue.
7. A removal override throwing before actual removal restores the pre-operation weapon and destination.
   A leave observer throwing after removal cannot invalidate the stored snapshot.

Native task restoration on rollback is best effort through the native adapter; a refused rollback logs the maid UUID and requested setting. Player-global legacy migration already observed after publication remains monotonic; rollback does not downgrade earned server progression. Interior remembered
position remains a valid observation even after rejection. Temporary phantom equipment/runtime links
are deliberately cleaned, not recreated from stale phantom snapshots; normal runtime maintenance
can recreate them. No real maid inventory is replaced with a historical backup. This is not a disk-wide
atomic transaction, nor a guarantee against power loss or arbitrary mods mutating objects in callbacks.

## Manifest transaction (actual sequence)

1. Entry owner/authorization/snapshot state gate; cross-dimension UUID and confirmed presence gate.
2. Read full compressed/legacy snapshot; validate snapshot owner, original UUID and stored binding.
3. Construct/load a native maid, clear old transfer flags, initialize stable binding/owner.
4. Select normal player position or the existing themed interior plot before the spawn packet. No plot
   means refusal. Execute the existing pre-spawn callback.
5. Revalidate candidate UUID/owner/binding and authorization/collision immediately before publication.
   This repeat is necessary because the callback may mutate the candidate or world.
6. `addFreshEntity` is the publication boundary. Refusal/pre-publication exception retains the weapon
   snapshot. A spawn-listener exception is classified using the actual server entity lookup, not just
   a boolean. A published entity completes the operation.
7. Preserve successful-path order: runtime preparation → destination manifested → stored payload removal
   → manifested Forge event → optional message → attention initialization. Runtime/notification errors
   are logged separately and cannot strand a live entity alongside an authoritative stored snapshot.

Forge listener priority/order inside the event bus is unchanged. A throwing listener can still stop
later listeners for that same event (Forge semantics); this code does not replay or reorder them.
The native `ContractEntityPresenceMixin` records accepted fresh spawns after `addEntity` returns true.

## State and identity principles

| State/information | Meaning | Authority |
|---|---|---|
| STORED | Current carrier has full entity payload | Must still decode and validate owner/UUID/binding |
| MANIFESTED | A matching live owned maid is loaded | Live entity is authoritative |
| TRANSITIONING | In-process scope for this ItemStack | Session-only guard for resident ability queries |
| SUSPECT_MISSING | No current loaded entity/payload | Diagnostic, never proof of death |
| RECOVERY_PENDING | Matching confirmed incident remains | Diagnostic/retained current removal data, not old backup permission |
| UNBOUND | No valid contract or superseded carrier | Not a recovery operation |
| Last deployment location | Observed dimension/block coordinates | Chunk recall hint, not an existence guarantee |
| Legacy incidents / reserved loss tasks | Historical diagnostic or recovery archive | Retained for save compatibility; old markers never grant spawn permission |
| Presence PRESENT / ABSENT / UNKNOWN | Confirmed world existence / removal / no observation | UNKNOWN stays unknown on detach; it must not become invented PRESENT |

UUID collision checks cover all loaded dimensions including other owners/non-maid entities. Binding
identifies the contract channel/interior; story origin/token verifies the genuine fox, not every physical
copy of a carrier. Creative copies are legal; one real UUID cannot be spawned twice.

## World presence and repair

| Situation | Recorded behavior |
|---|---|
| Successful fresh manifest/photo/film | Accepted server add records PRESENT; lifecycle records current WEAPON destination |
| Successful capture | Destination ABSENT, core transfer suppresses anomaly rescue |
| Canceled fresh add | No fresh presence write; snapshot remains |
| Chunk unload / dimension change / orderly restart | Preserve confirmed PRESENT; timeout does not erase it |
| Real killed/discarded entity | Leave event marks ABSENT, then preserves latest real snapshot or guides native backup |
| Native conversion to item | ToItem only clears presence for the actual server-published entity, not a temporary photo entity |
| Durability loss of known manifested maid | Keep real maid, detach destination; unloaded maid detaches on next join |
| Durability loss of unobserved legacy identity | Retain UNKNOWN, destination/fox ledger FREE_MAID; no fabricated existence fact |
| Third-party/offline deletion bypassing all events | Automatic proof is impossible; operator must audit unloaded chunks/all dimensions |

After a world audit proves the original UUID absent, an operator may hold/select the player's carrier
and run `/maidweapon recovery confirm-absent <player> after-world-audit`. It requires permission 2,
a real owner-bound contract, known journal schema and no loaded UUID conflict. The assertion is logged;
it only corrects current presence and runtime bookkeeping, never fabricates a maid or consumes a backup.
Then restore the existing full payload or use native `/tlm backup get <player>`. Do not run this merely
because a maid is unloaded. This deliberate operator assertion cannot replace an actual save audit.

## Recovery decision path

Only confirmed final durability consumption schedules a new rescue. At most 32 checks/pass,
2 restores/player/tick and 8/server/tick; tasks rotate, retry after 200 ticks, stop after 12 attempts.
Old unconfirmed/reserved/quarantined tasks remain readable and inert rather than deleting their only data.

1. Another entity/incorrect binding → INVALID_IDENTITY (retained under bounded retry policy).
2. No loaded maid, confirmed PRESENT → record deferred free destination, STILL_IN_WORLD; no film/spawn.
3. Full stored snapshot → try normal lifecycle manifest of a transient carrier.
4. Manifest refused → construct full native emergency film; successful delivery → FILM_DELIVERED.
5. Invalid/no full payload → NATIVE_BACKUP_REQUIRED; retain identity for native restoration.
6. Delivery refused with a valid snapshot → RETRY_LATER; keep queue snapshot.
7. Actual maid exists → clean runtime, detach binding, preserve progress/story identity → MAID_RELEASED.

Completed outcomes are dequeued before notifications. This is orderly retry protection, not exact-once
inventory delivery across crashes. A failed native TLM photo spawn may still consume its item: TLM owns
that transaction, and its backup command can provide another photo. No self-built backup system returns.

## Future ContractAbility extension

Register a stable ability ID in `ContractAbilityRegistry` during common setup, with a
`ContractAbilityProvider` validating genuine carrier ability ID and earned entitlement, an explicit
feature vocabulary, bounded numeric parameters, and an optional fallback definition. Data packs may
replace validated progression definitions; they cannot replace permissions or mint player mastery.
Query `ContractAbilityProgressionService` on the server and consume its feature set in the native
weapon/ability adapter. Use `ContractMasteryService` for player-owned growth and existing migration.
A staff addon should register its own adapter/provider; do not add staff branches to maid lifecycle,
recovery scheduler or Black Fox native sword art. Register optional linked classes only when installed.

## Boundaries to respect

Business callers should use carrier/storage/journal/ledger APIs, not invent `ForgeData`, destination,
compressed payload or WorldPresent structures. `ContractRecoveryJournal.destination` and presence APIs
centralize the frequently shared protocol; generic raw read/write methods remain for compatible archives
and diagnostics. `IntrinsicSpiritApi.proofKey` remains a compatibility helper but is no longer a security
cache key. Codec format/legacy reads, identity keys, native film extras and channel migration remain intact.
Tooltip composition is client-only; operator diagnosis is separate. Optional APIs remain behind compat.

## Retained design and remaining debt

Keep atomic compressed storage, server global mastery, generic registry, stable soul-slab bindings,
location tickets, weak carrier cache, native backup ownership and fox story ledger. These are distinct
responsibilities rather than redundant abstraction. Current high-level permission checks remain on public
facade and direct adapter entry for addon callers; commit checks after mutable callbacks have a separate reason.

Remaining debt: string destination kinds in legacy/addon protocol; wider raw NBT in channel/interior code;
best-effort optional/native rollback; orderly rather than crash-atomic persistence; copied contract comparison
cost in ability queries (not benchmarked yet); cancelled native-item consumption; explicit operator absence
audit for event-bypassing deletion. No claim of preventing malicious callback mutation or disk corruption.
No additional ability effects, levels, damage, 400-tick lifecycle, seven-block following or reward grants change.

See `ARCHITECTURE_REFINEMENT_VALIDATION.md` for this branch's actual execution evidence and boundaries.
