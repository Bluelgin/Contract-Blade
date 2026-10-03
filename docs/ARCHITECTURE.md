# Contract Blade Core architecture

The default branch is the maintained **Core**. The paused campaign/Part content
is an archive and must not leak gameplay rules back into Core.

## Dependency direction

```text
common data / domain
        |
        v
forge item + event entry points
        |
        v
contract services
   |          |
   v          v
deployment   TLM lifecycle
   |          |
   +----> compat adapters <---- optional mods
```

Optional mod APIs belong under `com.maidweapon.forge.compat`. Core systems may
call compatibility facades, but they must not import third-party APIs directly.

## Contract authority

A concrete contract is identified by its binding ID and owner. Serialized maid
state has a single authority at any moment:

- stored: compressed maid data on the real contract item;
- manifested: the live TLM entity;
- intrinsic spirit projection: one selected contract projected into the legacy
  root fields, with dormant archives kept outside the active authority.

The storage boundary is `ContractMaidStorage`; low-level TLM serialization is
owned by `ContractMaidLifecycleService`. Core callers use
`ContractLifecycleService`, so the Contract Table, dedicated Contract Blade
items and other interaction entry points request the same stored/live lifecycle.

`ContractCarrierData` owns shared metadata access for any carrier. The static
methods on `MaidWeaponItem` remain compatibility forwarders for existing addons;
Core services must not use the concrete item class as their metadata authority.
NBT keys and legacy defaults are unchanged.

`ContractAuthorization` accepts named additional authorization rules during mod
setup. Normal owner/identity checks still apply. Fox rules are registered by the
bootstrap, not hard-coded into the shared lifecycle. Every rule must allow an
action; an exception denies it and emits one diagnostic warning.

`IntrinsicSpiritApi` remains the public channel/transfer facade. Exact active
payload selection belongs to `ContractChannelStorage`; stored/live model changes
belong to `compat.tlm.IntrinsicSpiritModels`. Neither changes the save schema or
introduces another stored maid authority.

## Player interaction

Input handlers only decide which gesture owns an action. Permission and
capture/recall behavior go through `ContractInteractionService`. The Forge
bootstrap must never subscribe a second reflected TLM interaction path.

## Client presentation

Contract tooltip presentation is client-only under
`com.maidweapon.forge.client.tooltip`. Bound contract weapons of every supported
type use the same `ItemTooltipEvent` entry point and `MaidInfusion` access
facade. `MaidWeaponItem` only owns the unbound hint.

Modifier-key state such as Shift must be read only from this client package.
Common item code must not depend on `Screen` or reinterpret
`TooltipFlag.isAdvanced()` (F3+H advanced tooltips) as a modifier key.

## Contract interiors

Weapon interiors are a Core contract feature, not a deployment mode. Every
concrete contract is assigned one stable plot inside the single
`maid_weapon:contract_interior` dimension by binding ID.

The real player-owned interior is **land, not a generated house**. First entry
chooses one immutable terrain theme and seed. Contract level expands the usable
area in five monotonic rings; only the newly unlocked ring may be generated.
The player's older land, buildings and decoration are authoritative and must
never be globally rebuilt. Spatial stage footprints must be strictly nested;
a later stage must fully contain every earlier stage so removing the old
boundary can never expose a one-block void seam.

The interior stack is split into focused responsibilities:

- `ContractInteriorService`: enter/exit authority, return position and maid lifecycle;
- `ContractInteriorCarrierLocator`: locate the real inventory/cursor/menu carrier without copying it;
- `ContractInteriorSavedData`: binding-to-plot allocation, terrain theme/seed and generated stage;
- `ContractInteriorProfile`: maps contract level to the five usable-area stages;
- `ContractInteriorSelectionService`: one-time player terrain choice;
- `ContractInteriorTerrainBuilder`: deterministic incremental land generation and invisible boundary;
- `ContractInteriorBuilder`: developer/example Japanese home only; never the real player-home generator;
- `ContractInteriorGallery`: optional example-home/design workspace;
- `ContractInteriorEvents`: lifecycle safety and thin Home Life event dispatch.
- `interior.home`: clock, offline arrival policy, loaded furniture index and native activity observation;
- `compat.tlm.TlmHome*`: genuine TLM furniture interaction and reversible behavior scope;
- `ContractInteriorGuideService`: one guide receipt per binding after real entry.

Home Life is a behavior client of the existing lifecycle, never a storage authority.
It resolves only the current life slot on return and retains no offline chunk tickets.
See [HOME_LIFE.md](HOME_LIFE.md) for supported furniture, budgets and validation.

Built-in terrain themes keep a build-friendly central clearing while changing
the atmosphere of later land: plains, sakura, bamboo, lake islet and low hills.
Terrain generation is deterministic from `theme + seed + coordinates`.
Favorability does **not** place furniture or rewrite terrain; it is reserved for
future maid-at-home behavior and interaction. Resonance likewise remains a
temporary ambience concern rather than permanent world geometry.

The real TLM maid remains the only manifested maid authority inside the
interior. Entry manifests from the contract after teleport; exit captures the
same entity back into the same binding before returning the player. Interior
code must not depend on the hotbar deployment state machine. Conversely, the
hotbar deployment state machine explicitly ignores the interior dimension so it
cannot adopt or recall the maid manifested by the interior lifecycle.

The example-home gallery and external schematics are presentation/development
assets only. They must never become runtime dependencies of contract identity,
terrain progression or maid lifecycle.

## Deployment

`InfusedMaidDeploymentSystem` maintains the shared live lifecycle and recovery
for **all** infused carriers, including dedicated Contract Blades. It does not
summon merely because a carrier is held. Manual input and protective damage
requests go through `ContractCompanionService`; switching held items does not
dismiss a manually called companion. Contract Blades have no separate
sneak-right-click recall path.

Focused services own independent responsibilities:

- `ContractWeaponLocator`: resolve the real contract and live maid;
- `ContractCombatTaskRouter`: optional-mod combat task selection/fallback;
- `ContractDeploymentEffects`: presentation only;
- `ContractRecoveryService`: cross-chunk/dimension recovery and region tickets;
- `ContractTransferSafetyService`: inventory/self-storage transfer invariants;
- `ContractMaidRuntimeService`: manifested-maid task and optional-mod projection maintenance.

Other systems, including the existing interior/home dimension, depend on
contract identity/storage APIs, not on the hotbar deployment state machine.

## Companion intent and input

`ContractCompanionKeys` registers client-only input. An empty, serverbound
`ContractCompanionNetwork.Call` carries intent, not an item, position or entity
chosen by the client. The server validates ownership, main hand, menus, cooldown,
dimension and range in `ContractCompanionService`.

`ContractCompanionState` persists MANUAL/GUARD/RESIDENT intent and guard expiry
on the real carrier and entity; it never contains another entity/inventory copy.
`TlmResidenceAdapter` reads native home mode or ordered sitting, not temporary
furniture seating. Only loaded entities are consulted; ordinary requests never
use recovery tickets. `InfusedMaidDeploymentSystem` still owns lifecycle and
emergency film safety, but its tick loop no longer requests hotbar manifestation.
`ContractCompanionDialogue` provides rate-limited owner-only text, without healing.

## Native maid baubles and projection

`ContractBaubleExtension` registers items through TLM's public extension API.
`TlmProjectionBaubles` reads only native maid bauble slots and resolves one
exclusive `ContractProjectionMode`; it never reads player accessories. The knot's
combined mode enables both capabilities through the same policy, with no second
projection engine or task authority. All bauble recipes use TLM's native altar
serializer and normal item outputs; no custom altar logic is introduced.

`ContractMaidRuntimeService` coordinates three separate responsibilities:

- `ContractEquipmentProjection`: slot-scoped original equipment snapshots,
  projection/restore transactions and migration of legacy copied attributes;
- `ContractWorkPolicy`: remember/restore original work only while weapon
  resonance is active; without it, manual work remains authoritative;
- `ContractCombatTaskRouter`: select optional combat integrations only for
  active weapon resonance.

`TlmEquipmentReturns` returns displaced originals to the native backpack.
Contract-home handling takes priority over all projection and combat policies.
No projection copies player base attributes or redistributes third-party assets.

## Legacy Part data

Old `EmbeddedSins` data is preserved when old weapons are loaded and rewritten,
but it is archival metadata only. Core combat, resonance and progression must
not read it. `SinFragmentSystem` remains an inert compatibility shim.

## Guardrails

`CompatDiagnostics` reports reflective capability failures once per process,
preserving existing safe fallbacks without flooding tick logs. Missing optional
mods remain normal; failed capability invocations are not silently hidden.

`tools/validate_architecture.py` protects dependency boundaries and god-class
size. Other validators cover NBT safety, permissions, resonance, emergency film
recovery, Patchouli resources and the legacy/Core boundary. CI runs validators
before the Gradle build.
