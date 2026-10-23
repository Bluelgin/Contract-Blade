# Contract resonance foundation

> Current gameplay: manifestation is the fast-recovery phase. Stored idle +2.5/s, stored combat 0/s; manifested idle +20/s, manifested combat +10/s at the default 1000 pool, before spirit-vein multipliers. Call/recall and maid injury/protective recall are free, including at zero resonance; protective recall preserves injury and cooldown. The foundation implementation notes below describe earlier revisions. Current costs, V2 configuration migration and behavior are in [SPIRIT_RESONANCE_COSTS.md](SPIRIT_RESONANCE_COSTS.md).

## Scope and baseline

Built independently on master `3e2cf94007ba531d20150863fdb32e63f98648b2` (Tooltip PR #24). Open combat PR #25 was inspected at `64dffaefb10749661f422c3b987715dc572bfadd`; its branch is not modified or incorporated. There are no new Akatsuki, black-fox or white-fox skill costs and no changes to their damage, animation, frequency, cooldown or growth parameters. BladeTetra remains optional.

## Authority and persistence

`ResonanceProgressSavedData` owns `data/maid_weapon_resonance_progress.dat` in the server overworld. SchemaVersion=1, Players maps the owner's UUID to {SchemaVersion:1, Stage:n}. Death, dimension changes and carrier transfers do not copy or reset this data. New contracts look up their owner's current stage. Newer root/player schemas remain read-only and are preserved, independently from ContractMastery.

The existing per-channel `MaidData.ContractResonance` integer and server maximum (default 200) remain intact. No progress or fractional-recovery fields are added to weapons or maids. Unknown ContractSchema (>2), unknown recovery journal and unsupported compressed payloads cannot be mutated through this API.

## Recovery

One END-player-tick listener enumerates the active, external and intrinsic archived channels. Authoritative UUID/binding/owner, current destination, world entity, native ownership and real carrier reference are checked. Ambiguous copies of one binding or maid are rejected. The session accumulator is keyed by owner UUID and maid UUID, so moving a maid between channels/items cannot duplicate a tick or reset the remainder to grant free recovery.

Default resonance/second, multiplied by the owner's global spirit vein:

| State | Rate |
|---|---:|
| Stored in weapon | 1 |
| Manifested, idle (including residence) | 2 |
| Reported ongoing ability, idle | 1 |
| Combat / attacking ability / berserk ability | 0.5 |

Rates are read from ContractResonance in `maid_weapon-common.toml`: `storedRecoveryPerSecond`, existing `passiveRecovery`, `abilityIdleRecoveryPerSecond`, `combatRecoveryPerSecond`. `recoveryDelay` now means the recent-combat activity timeout, not a complete recovery stop. Reports expire after 1–1200 ticks, are separated by ability source, and the most restrictive active state wins. Actual mob targets and server damage events supply current facts; client effects never supply combat state.

One million integer units equal one resonance. All requested defaults, including ×1.25 and ×1.60, are exact per tick; configurable rates are rounded to micro-resonance per tick. No elapsed time is multiplied into a reward. Unloaded/offline contracts receive no catch-up. Fractional session state clears on logout/server stop and never crosses maids. Full-cap surplus is discarded and lowering the cap clamps the stored integer on the next valid recovery pass.

The old MaidBondCombatHandler idle +2 listener is removed. Combat upkeep, maid damage and emergency costs use the common waiver-aware drain path; emergency recall itself is never waived. Existing validated kills, cooperation, food, healing and spirit crystals remain resource sources. The unconditional owner-hurt reward now defaults to **0**, with the existing multiplier configuration retained (old explicitly configured values are preserved). This does not remove the existing legitimate owner-healing recovery rule.

## Permanent consumables and recipes

The stages are 0/1/2/3 → ×1.00/×1.25/×1.60/×2.00. `spiritVeinMultipliers` is the ordered configuration definition; all item classes share one progression service. Repeated, skipped or unsupported stages do not change data or consume items. Creative use retains the item. Upgrades neither fill resonance nor increase its maximum.

Awakening, Flowing and Endless Spirit Veins have progressively more costly six-ingredient TLM altar recipes; the last requires a nether star and netherite. Endless Contract upgrades Heartbound Knot with late-game vanilla materials. All ingredients are available without optional mods. All four items have creative entries, bilingual names/descriptions, models, transparent 32×32 animation frames and manually redrawn SVG source in `art/resonance`. `item-preview.gif` shows the approved v2 motion, and `item-preview.png` includes a 16×16 readability check. Each native PNG is a 32×1280 strip: 40 frames × 2 ticks, a 4-second loop, with interpolation disabled. The generated concept is a design reference, not a resized final texture.

## Endless Contract

`ContractBaubleEffects` composes WEAPON_PROJECTION, ARMOR_PROJECTION, AUTO_WORK and RESONANCE_WAIVER. Existing projection precedence remains the first equipped projection bauble. The ultimate item reuses the same COMBINED runtime as Heartbound Knot; no duplicate task/equipment/feeding implementation exists. The waiver is separately found in actual native bauble slots, so another projection bauble's precedence does not accidentally disable a worn waiver.

Live maids are inspected through `getMaidBauble()`. Stored maids are read through `ContractMaidStorage` and its bounded, checksummed codec. UUID, owner and any stored binding are verified; the native saveWithoutId format is supported and a contradictory explicit entity ID is rejected; only `MaidBaubleInventory.Items` in the verified TLM 1.5.3 native layout grants waiver. Backpack items and the player's inventory are never interpreted as equipped baubles. Unsupported TLM versions/layouts fail closed for waiver; valid payload identity may still receive ordinary resource service.

The bounded 256-entry / 32 MiB session cache uses owner+maid+binding and an independent snapshot of **all** codec payload fields, including the compressed bytes. Equality checks do not trust a CRC alone. Any serialized slot change, capture, load, transfer or channel payload replacement causes a miss; live slots are always read directly. Logout and server stop clear caches. No full entity is instantiated or decompressed every tick. The cached snapshot also detects in-place payload mutations.

## Extension API

Server-thread callers use `ResonanceService` with the actual carrier reference, player and explicit binding. Active-channel helpers can use `ContractCarrierData.getBindingId(carrier)`; archived channels pass their own binding. The service re-resolves authority on each call; a copied ItemStack, superseded contract, projection phantom, conflicting duplicate, foreign owner, invalid identity or unsupported schema cannot gain benefits. No client-declared waiver flag exists.

```java
var operation = ResonanceService.newAttempt(); // one object per logical execution
var result = ResonanceService.pay(owner, carrier, binding, 12, operation);
if (ResonanceService.succeeded(result)) {
    // Execute this operation once, preserving its existing level/cooldown/safety rules.
}
```

`canPay` is a read-only preview, never an authorization token or charge. `pay` revalidates, returns PAID/WAIVED/INSUFFICIENT/UNAUTHORIZED/INVALID_COST, and reusing an already successful attempt returns ALREADY_PAID (not success). Callers retain the attempt through retries/callbacks; making a fresh attempt for the same logical operation is not a supported idempotence strategy. There is no unbounded persistent receipt table.

Other entry points: `current`, `maximum`, `recoveryMultiplier`, `waived`, `recover`, `activity`, `reportActivity`. A future ongoing ability refreshes its source report as needed; it does not modify persistence or bauble resolution. `drain` is reserved for existing mandatory upkeep/damage penalties that must saturate at zero rather than refuse the entire debit. The common detached-data ResonanceSystem.spend helper is deprecated for runtime integrations. Akatsuki's native SoulResonanceService is untouched and unrelated.

## Validation

- JUnit `ResonancePolicyTest`: all default rates/multipliers for 72,000 ticks, duplicate settlement, full/zero/lowered cap, independent fractional state, session restart/no offline catch-up, ordered upgrades and mock cost decisions.
- `ContractResonanceValidation` / `--fixture resonance --compat vanilla`: real TLM capture/manifest, native and stored baubles, removal/cache invalidation, independent contracts, duplicate references, foreign/copy rejection, mock payment and once-only attempts, zero-resource waiver, emergency recall, global item consumption, activity recovery, cap reduction, archive channels, recipes and SavedData persistence. Runner also starts the same disposable world again and requires `CONTRACT_RESONANCE_RESTART_PASS`.
- Static guards: `validate_resonance_foundation.py` plus the existing architecture, permissions, NBT, lifecycle, resonance, projection, companion, item and tooltip validators.
- Human client checks still required: actual inventory/JEI appearance, particles/audio, UI feel, optional combat-mod equipment projection and full multiplayer/dimension scenarios. A dedicated-server fixture is not a human client playtest.

Execution results and any unavailable checks are reported in the PR. A fixture existing in source does not mean it has passed.

## Server-owned resonance pools (migration revision)

The authoritative short-term balance is now `data/maid_weapon_resonance_pools.dat`,
keyed by owner UUID and maid UUID. The weapon still carries binding/identity and old
`MaidData.ContractResonance` for historical archive compatibility only; runtime
recovery, payment, command changes and mandatory combat deductions no longer write
the weapon's resonance NBT. `maid_weapon_resonance_progress.dat` continues to own
the independent player-wide spirit-vein upgrade stage.

`ResonancePoolSavedData` lazily imports a validated legacy channel **once**,
recording its old configured ceiling and converting proportionally:
`round(old / legacyMaximum * poolMaximum)`. For example 120/200 becomes 600/1000.
Records already imported can never be refreshed from old weapon NBT, so moving
between the external and intrinsic archived channels does not replenish a pool.
Old `Resonance.maximum` is retained as the migration denominator and for
backward-compatible legacy tuning; new `Resonance.poolMaximum` defaults to 1000.
Do not remove or change the old config file before importing a pre-existing world.
Back up worlds before upgrading. Cross-server item transfers do not export SavedData.

Pre-pool gameplay rewards and deductions are converted using the ratio of the two
configured ceilings. New extension API `ResonanceService.pay` and `recover` work in
native pool units; `recoverLegacy` / `drainLegacy` are for old call sites only.
Natural recovery is settled once per second in exact fractional micro-units, without
offline catch-up. The resolver still strictly validates identity on first import and
on actual payments; already-verified balance readbacks may use the lighter
`resolveIdentity` authorization path without repeatedly decoding stored maid NBT.

Server-to-client `ResonanceNetwork` sends owner-only UUID/balance/cap snapshots only
on change; client HUD and tooltips do not interpret old weapon NBT as live balance.
While joining or switching to an uncached contract the tooltip displays a syncing
placeholder. No clientbound snapshot is accepted as a server payment authorization.

Future standalone improvements: use a lower-cost live contract-index cache when the
holder's inventory contains many independent contracts, measure packet sizes and
game-thread time with realistically large embedded maid data, and validate
complete singleplayer/multiplayer interactions. No named fox skill costs are added.

## Spirit route ability costs after PR #27

The original #26 PR implemented **foundation only** (as documented above). Subsequent PR #27 adds active expenditures for Black Fox, White Fox and Akatsuki and reduces Manka's talisman costs. Complete route costs and the source-of-truth recovery list are documented in [SPIRIT_RESONANCE_COSTS.md](SPIRIT_RESONANCE_COSTS.md). The black-fox native Proud Soul expense is preserved. White-fox oath's basic fatal-hit survival is still available at zero resonance, and Akatsuki's domain rescue remains independent of funded offensive pulses.
