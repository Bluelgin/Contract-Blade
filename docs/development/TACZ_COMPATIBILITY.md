# TACZ contract projection compatibility

## Scope and confirmed causes

Target runtime: Minecraft 1.20.1, Forge 47.4.10, TLM 1.5.3 and TACZ
1.1.8 (CurseForge file 8055552). The released TACZ JAR was inspected with
`javap`, in addition to upstream source. TACZ upstream HEAD during investigation
was `b482eff`; TLM was `8a3ac5e` (its properties specify 1.5.3).

Confirmed defects:

* `createProjection` unconditionally called `setDummyAmmoAmount(copy, 0)`.
  TACZ `GunItemDataAccessor.useDummyAmmo` tests **tag presence**, not whether its
  value is positive. Ordinary guns therefore skipped inventory ammunition and
  saw an empty virtual reserve. This predates PR #29: it exists in `e3a4303`
  (1.0.6), as well as `dd9b27f` and #29's parent `0d8c700`.
* The old link occupied the last available `getMaidInv` slot. TLM's null-facing
  item capability exposes armor, hands, maid inventory and baubles. TACZ walks
  it forwards: maid rounds could precede the link, and a full inventory had no
  link slot. This cannot implement owner-first priority.
* Generic main-hand projection ran before the specialized gun projection,
  creating two restoration snapshots. This also predates #29. #29 changed cleanup
  ordering and respected manual replacement, but retained the overlapping owners.
* `isPhantom` only recognized the generic marker. TACZ-marked copies bypassed
  transfer/purge/drop predicates; native hand slots had no extraction boundary.

These are source/bytecode findings. They do not prove every reported symptom
was newly introduced by #29. In particular, the primary empty-dummy defect was
not a #29 regression. No automatic work switching or work-mode notification is
introduced by this change.

## Native calls and supply

TLM `TaskGunAttack` / `TacInnerCompat.performGunAttack` uses `IGunOperator` for
shoot, draw, bolt and reload. It does not need a replacement combat task.

The optional mixins adapt only TACZ's `LivingEntity.getCapability(ITEM_HANDLER,
null)` calls in:

* `AbstractGunItem.canReload` and `hasInventoryAmmo`;
* `ModernKineticGunScriptAPI.consumeAmmoFromPlayer` and `hasAmmoToConsume`;
* `LivingEntityShoot.consumeAmmoFromPlayer` (including direct chamber supply).

For an authorized projected maid these return a temporary view ordered as owner
slots, then native maid slots. No capability is replaced or installed, no inventory
slot is reserved, and no availability count survives a call. Other entities retain
the original capability result. Each owner access revalidates server thread,
online owner, native tame ownership, dimension, 64-block distance, active bauble,
non-resident status, source binding and matching projected gun. Failed owner
access leaves the maid's native supply available.

`findAndExtractInventoryAmmo` delegates to the view only when the argument is our
view. Loose `IAmmo` rounds use real `extractItem`; boxes use the real `IAmmoBox`
instance and debit is measured after mutation. A copy-only item handler is not
accepted as a mutable box source. Empty boxes and projections are hidden. Native
creative boxes keep their free-supply semantics. No client supplies a count.
Server-thread execution serializes multiple maids using the same owner inventory.
A repeated extraction request must debit again; it cannot reuse a cached grant.

Native `useInventoryAmmo`, feed type, reload scripts, attachment capacity, chamber
and infinite-reload logic remain in TACZ. Ordinary projections no longer acquire
`DummyAmmo`; already-saved projections have that accidental tag removed without
replacing their live magazine. Real virtual-ammo guns keep their mode: supported
native checks read the authoritative source gun's dummy reserve and extraction
debits that reserve synchronously, rather than copying it.

## Projection lifetime and protection

`ContractProjectionIdentity` recognizes both historical markers without loading
TACZ. Existing transfer, purge, drop and ownership users inherit that predicate.
The generic equipment manager skips TACZ main hand; offhand/armor remain generic.
The specialized manager snapshots the original real hand once and preserves the
live projected stack. Old nested snapshots collapse into one real original.
Manual real replacements are preserved and the saved original is returned once.

Service-side guards reject clicks targeting a projection before quick move,
number-key swap, clone, throw or drag can mutate the menu. Slot pickup checks
also cover collection operations. Forge equipment and standard backpack handlers
reject simulated and actual extraction of marked copies. Item spawn/toss and
living-drop guards reject escaped projections. These checks do not refill a
removed projection next tick. Trusted internal `setItemSlot`/`setStackInSlot`
remain available to lifecycle restoration; integrations that deliberately mutate
a live `ItemStack` reference or bypass Forge/vanilla boundaries are outside that
API contract.

The old link registry ID remains as an inert item, without `IAmmoBox`. Links are
removed on the first migrated maid maintenance and teardown; existing player
cleanup removes old leaked links. There is no new per-tick inventory scan.

Remaining paid rounds are capped by a per-projection debit ledger on teardown;
native `reduceAmmoOnce` reductions spend credits. Inventory-direct shots do not
accumulate fictitious refundable magazines. Creative/infinite supply cannot be
materialized as physical refund items. Virtual rounds return to the bound source
reserve; fuel follows native discard semantics. Source magazine/chamber ammo is
never copied into the projection. The real source weapon remains untouched by
equipment cleanup. Legacy projections have no historical provenance ledger, so
migration preserves the existing loaded-round refund convention.

## Verification and limits

Run the native provider fixture with:

```sh
python tools/run_stage_three_tests.py --fixture projection --compat tacz
```

It uses the pinned real mods in a disposable dedicated-server world and requires
`TACZ_AMMO_VALIDATION_PASS`, `TACZ_OWNERSHIP_VALIDATION_PASS` and
`PROJECTION_VALIDATION_PASS`, plus `TACZ_OWNER_ROUTE_VALIDATION_PASS`. Tests execute TACZ's extraction entry point with
real TACZ rounds/boxes and exercise production inventory-view code. The arithmetic fixture supplies an explicit access predicate. A second fixture
registers a fake server player temporarily in the disposable world's online map,
binds/spawns a real maid, and uses the production authority resolver and native
script API. It checks owner priority, native magazine insertion/shot reduction,
range/logout fallback and conservation after teardown. This is a synthetic server
session, not a network-connected player or live AI combat test.

Covered cases: owner-only, split supply, maid-only, insufficient/empty supply,
shared owner inventory, revoked owner supply, live native box debit, stale
presence reads, projection identity, Forge extraction, main-hand restoration,
manual replacement, single snapshot and menu extraction gestures.

These are not equivalent to a full native combat test. Still require live-player
verification: actual `gun_attack` reload/continuous fire, all authorization
transitions (logout, dimension and range), restart with loaded projections,
virtual reserves, infinite/creative configurations, fuel weapons and third-party
gun scripts. Custom scripts that directly mutate gun NBT or bypass TACZ extraction
and `reduceAmmoOnce` also bypass its normal ammo contract and debit accounting.
No universal compatibility claim is made for such scripts or arbitrary custom
item handlers. Existing client video CI is unrelated to TACZ shooting evidence.
