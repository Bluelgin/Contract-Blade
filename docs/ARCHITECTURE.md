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
- `ContractInteriorSavedData`: binding-to-plot allocation, terrain theme/seed and generated stage;
- `ContractInteriorProfile`: maps contract level to the five usable-area stages;
- `ContractInteriorSelectionService`: one-time player terrain choice;
- `ContractInteriorTerrainBuilder`: deterministic incremental land generation and invisible boundary;
- `ContractInteriorBuilder`: developer/example Japanese home only; never the real player-home generator;
- `ContractInteriorGallery`: optional example-home/design workspace;
- `ContractInteriorEvents`: logout/death/void/drop safety only.

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

`InfusedMaidDeploymentSystem` is the single main-hand manifestation/recall
state machine for **all** infused carriers, including the dedicated Contract
Blade items. Contract Blades no longer own a sneak-right-click recall path;
drawing/putting away the carrier follows the same rules as a weapon created by
the Contract Table.

Focused services own independent responsibilities:

- `ContractWeaponLocator`: resolve the real contract and live maid;
- `ContractCombatTaskRouter`: optional-mod combat task selection/fallback;
- `ContractDeploymentEffects`: presentation only;
- `ContractRecoveryService`: cross-chunk/dimension recovery and region tickets;
- `ContractTransferSafetyService`: inventory/self-storage transfer invariants;
- `ContractMaidRuntimeService`: manifested-maid task and optional-mod projection maintenance.

Future systems such as a weapon interior/home dimension should depend on
contract identity/storage APIs, not on the hotbar deployment state machine.

## Legacy Part data

Old `EmbeddedSins` data is preserved when old weapons are loaded and rewritten,
but it is archival metadata only. Core combat, resonance and progression must
not read it. `SinFragmentSystem` remains an inert compatibility shim.

## Guardrails

`tools/validate_architecture.py` protects dependency boundaries and god-class
size. Other validators cover NBT safety, permissions, resonance, emergency film
recovery, Patchouli resources and the legacy/Core boundary. CI runs validators
before the Gradle build.
