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

The storage boundary is `ContractMaidStorage`; live/stored transitions are owned
by `ContractMaidLifecycleService`.

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

The interior stack is split into focused responsibilities:

- `ContractInteriorService`: enter/exit authority, return position and maid lifecycle;
- `ContractInteriorSavedData`: persistent binding-to-plot allocation and built stage;
- `ContractInteriorProfile`: maps contract level/favorability to visual progression;
- `ContractInteriorBuilder`: built-in fallback home and reserved dynamic decorations;
- `ContractInteriorEvents`: logout/death/void/drop safety only.

Contract level controls monotonic spatial growth. Favorability controls
reversible lived-in details at reserved positions. Player edits must never be
globally rewritten on every entry.

The real TLM maid remains the only manifested maid authority inside the
interior. Entry manifests from the contract after teleport; exit captures the
same entity back into the same binding before returning the player. Interior
code must not depend on the hotbar deployment state machine.

Visual homes are replaceable presentation assets. External schematics/templates
must have explicit redistribution/modification terms and must not become a
runtime dependency of the contract identity or lifecycle layers.

## Deployment

`InfusedMaidDeploymentSystem` remains the orchestration state machine. Focused
services own independent responsibilities:

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
