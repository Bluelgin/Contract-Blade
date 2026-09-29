# Contract Home Life

Home Life runs only on the existing real contract maid inside a selected player
plot. It never manifests, captures, copies maid NBT, creates a proxy, generates
furniture, or owns a chunk ticket. `ContractInteriorService` alone resumes the
same maid through `ContractLifecycleService` on entry/login and captures her on
exit/logout. The gallery remains separate.

## First supported interactions

- TLM **maid bed**, head half: real `startSleeping`/`stopSleeping`, as used by
  `MaidBedTask`, including TLM's sleep pose and favorability hook.
- TLM **chair entity / cushion**, when `isTameableCanRide()` is enabled: real
  `startRiding`, as used by `MaidFindSitTask`. Actual passengers determine occupancy.
- TLM **Gomoku / Chinese chess / western chess**: real `BlockJoy.startMaidSit` +
  `EntitySit`, with the native `board_games` task and its `canSitInJoy` contract.
  Home Life temporarily uses TLM's ALL schedule only for this action, then returns
  to the scoped idle task; the player's original schedule is restored on release.
- `WANDER`, `STAY_NEAR_PLAYER`, `IDLE` are safe non-furniture choices.

`COOK`, `WORK`, `READ`, `GARDEN`, `OBSERVE` remain vocabulary reserved for
adapters, not claimed working interactions. Vanilla beds, generic tables, kitchens
and arbitrary third-party furniture are not automatically supported. TLM
bookshelf/computer/keyboard Joy blocks remain deferred: `EntitySit.tickMaid` only
permits IDLE (or a compatible WORK task), while the shared Contract Interior has
fixed world time. We do not change the whole dimension clock merely to keep one
maid seated.

Source audit: TartaricAcid/TouhouLittleMaid `1.20`, commit
`8a3ac5e9eacf0c6d63adf8832949f3ce81484fe6`; see `MaidBedTask`, `MaidFindSitTask`,
`MaidClearSleepTask`, `MaidUpdateActivityFromSchedule`, `SchedulePos`, `EntitySit`,
`BlockJoy`, `EntityChair`. CI's native fixture uses the pinned 1.5.3 Forge snapshot
from `snapshot-2026-05-09-05-17-04`, SHA-256
`8c341567e57aa3c006f9fa85c7a5e3bc36a53b1cca4e3538c3db705eeeea1bbc`.

## Responsibilities

- `ContractHomeClock`: pure conversion from `Instant` or overworld ticks into
  date, local minute, 30-minute slot and broad phase.
- `ContractHomeOfflineState`: per-binding saved clock, receipt, last selection,
  target identity, seed and timestamps. No simulated offline AI loop.
- `ContractHomeActivityResolver`: deterministic weighted choice, canonical enum
  ordering, binding + maid UUID + slot seed, unavailable activities filtered out.
- `ContractHomeFurnitureRegistry`: per-online-session loaded-only index. Entries
  carry source, position, type, weight and occupancy. Extensions implement
  `ContractHomeFurnitureAdapter` and register during server setup.
- `ContractHomeRuntime`: 30-second decisions; one-second cheap progress/safety
  checks; 20-second approach timeout; failed furniture excluded for the slot.
- `TlmHomeBehaviorController`: temporary scope on the same maid's real brain.
  Native navigation/look/swim behaviors and TLM door interaction run normally. TLM's world-time schedule
  and independent furniture selection are excluded inside this scope so they do
  not instantly wake a real-time sleeper or override the chosen target.
  Small original task/schedule/home/sitting/position settings are retained for
  crash recovery; `refreshBrain` restores TLM behavior on release. Restore
  failure is observable at the lifecycle boundary: capture is refused rather
  than serializing a temporary Home Life brain/task/schedule into the weapon.
  Inventory/entity NBT is never copied. Fire, injury, drowning, combat or a leash yields to TLM.
- `ContractInteriorGuideService`: a localized written book after successful
  real entry; receipt belongs to the plot. Full inventory defers delivery and
  retries every 30 seconds while at home.

## Clock commands

Inside your own selected home, carrying the valid owned binding:

```
/contractinterior clock minecraft
/contractinterior clock realtime
/contractinterior clock server
/contractinterior timezone +8
/contractinterior timezone +05:30
/contractinterior timezone Asia/Shanghai
```

Minecraft is the default and reads **overworld** day time (tick zero = 06:00).
Real time uses `Instant.now()` and the plot's explicit offset/zone; default UTC.
Server time uses `-Dcontractblade.home.serverZone=Asia/Shanghai`, default UTC.
No machine-local timezone is inferred. Named zones follow DST; repeated local
wall-clock slots retain their selection. Changing settings invalidates the slot.
No visual sky-time synchronization, acceleration or GUI is provided yet.

06–10 morning; 10–17 day; 17–23 evening; otherwise night. These are weight
biases, never mandatory bedtimes. Higher favorability increases proximity choice
(3 to 35) and adds weight to furniture within eight blocks of the owner. It does
not place blocks. A valid existing selection is retained within the same slot.

## Work budget and offline behavior

Entry and a low-frequency refresh inspect loaded chunks' block-entity maps and
loaded entities, not every block in a volume. Block/entity events coalesce edits;
refreshes are spaced by at least 20 seconds, with a 60-second fallback for
commands, moved chairs, third-party edits and loaded chunks. Caps: 8192 inspected
block entities, 8192 entity candidates, 512 indexed targets. Only the plot's
nested footprint qualifies; other BindingIds and gallery regions are excluded.
All candidates are revalidated before use and running furniture is checked once
per second. Removed/moved/occupied furniture cannot hold stale navigation forever.

On normal absence the real maid is captured by the existing lifecycle, and the
session/index is discarded. A crash-restored or uncaptured home resident is
paused while unattended without loading any additional chunks. On return the
current slot is resolved directly, preserving a valid same-slot selection.
Twelve hours away means one resolution, not twelve hours of simulation.

## Verification

`python tools/validate_home_life.py` compiles/runs pure-Java boundary and resolver
checks plus architecture tripwires. Existing validators remain in Core CI.
`gradle clean build -Pci=true` is the full build gate.

The existing headless preview workflow now includes the pinned TLM runtime and
an explicit `contractinterior validate-home` fixture (available only with
`-PgalleryPreview=true`). It checks plot NBT roundtrip/isolation, real bed/chair/board-game APIs,
target invalidation and restoration of the same maid's brain/task/schedule. Preview still
uses real Forge saves, `save-all flush` and `.mca` rendering for Sakura 1–5 and
all five maximum-stage themes. These server checks verify state and APIs;
client animation appearance and complex player-built routes still need in-game
acceptance testing.


## Interior containment

An active home session is bound to its own generated plot footprint. Cross-dimension
travel is handled by the normal lifecycle event, while same-dimension teleports
(such as a waystone inside the shared Contract Interior dimension) are checked on
the regular safety tick. A teleport outside the active binding's plot returns the
owner to that plot's origin instead of exposing another binding's cell. This check
is horizontal only, so vertical player builds inside the owned footprint remain
usable.
