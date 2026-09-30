# Contract Home Life

Home Life runs only on the existing real contract maid inside a selected player
plot. It never manifests, captures, copies maid NBT, creates a proxy, generates
furniture, or owns a chunk ticket. `ContractInteriorService` alone resumes the
same maid through `ContractLifecycleService` on entry/login and captures her on
exit/logout. The gallery remains separate.

## Carrier interaction safety

Dedicated Contract Blades bind directly by main-hand right-click on the owner's
tamed maid; a Contract Table is optional for these items. The same deployment
state machine manifests the real maid while held and recalls her when switched away.

Picking up the active carrier in the inventory is not an interior exit. Normal
mouse cursors remain untouched until the menu closes. Creative inventory cursors
are client-only, so temporary server-side absence preserves the established plot
session instead of teleporting the player or creating a replacement carrier.
Home activity resumes once the real carrier returns. External-container rescue
and cursor-close rescue preserve the original carrier NBT; projection phantoms
never authorize these operations.

The native `contractinterior validate-home` fixture covers real inventory PICKUP
clicks, creative-cursor absence, cursor-close rescue, direct dedicated binding,
automatic manifestation and switch-away recall using a real TLM maid.

## Resident continuity and equipment

Home checkpoints store only the real maid's UUID, position and rotation in the
plot's `HomeLife` state, never a second entity or inventory archive. Reentry
restores that position, with a collision/plot-boundary fallback if building edits
make it unsafe. An already-live resident is not moved to the entry point.
Activity choices are refreshed on return; unattended plots still have no AI or
chunk tickets. Waking activities may change after two minutes, and wandering or
near-player movement updates every five seconds while the main choice cadence
remains thirty seconds.

Interior manifestation/maintenance bypasses combat projections. Existing leaked
projections are released before Home Life starts. TLM equipment slots are locked
while the contracted maid is inside; backpack slots reject weapons/armor but keep
ordinary food and items usable. Real menu PICKUP, QUICK_MOVE and SWAP tests assert
that rejected items remain with the player and original gear survives recall.

The sky no longer has a fixed-noon dimension setting. Each interior viewer gets
time updates for their plot's clock, after the server's normal time updates;
the shared world clock and other plots are not modified. Minecraft mode follows
exact overworld ticks, while real/server modes use the same zone calculation as
activity selection. System settings in the guide are explicitly labeled system
instructions; existing guides are upgraded in place without redelivery.

## First supported interactions

- TLM **maid bed**, head half: real `startSleeping`/`stopSleeping`, as used by
  `MaidBedTask`, including TLM's sleep pose and favorability hook.
- TLM **chair entity / cushion**, when `isTameableCanRide()` is enabled: real
  `startRiding`, as used by `MaidFindSitTask`. Actual passengers determine occupancy.
- TLM **Gomoku / Chinese chess / western chess**: real `EntitySit` + the native
  `board_games` task contract. The maid remains the real board passenger, so the
  board's normal owner checks, game state, client packets and win/favorability logic
  still see the real TLM maid.
- TLM **bookshelf**: real `EntitySit` and TLM's native bookshelf animation, exposed
  as `READ`.
- TLM **computer / keyboard**: real `EntitySit` and their native TLM animations,
  exposed as `PLAY`.
- TLM **picnic mat**: real picnic `EntitySit` plus TLM's own `MaidHomeMealTask`.
  Food stays in the picnic mat's native item handler; the real maid takes one item
  through TLM's meal API, hand/backpack handling, favorability cooldown and trigger path.
  This is exposed as `MEAL`.
- `WANDER`, `STAY_NEAR_PLAYER`, `IDLE` are safe non-furniture choices.

The shared Contract Interior world clock is never changed to satisfy TLM's
world-time schedule. For bookshelf/computer/keyboard/picnic activities, Home Life
marks the exact selected `EntitySit` UUID and cancels that seat's schedule-driven dismount
while the activity is active. The marker is removed before normal stop/restore,
so arbitrary TLM seats and manual lifecycle cleanup are not globally blocked.

`COOK`, `WORK`, `GARDEN`, `OBSERVE` remain extension vocabulary rather than
claimed interactions. Vanilla beds, generic tables, kitchens and arbitrary
third-party furniture are not automatically supported.

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
  The wider contract runtime also snapshots the maid's pre-contract TLM task
  **and schedule** before forcing combat-time `ALL`, restores both before
  capture/emergency-film serialization, and strips those temporary tags from
  cleared or projected contracts. Inventory/entity NBT is never duplicated.
  Fire, injury, drowning, combat or a leash yields to TLM immediately.
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
paused while unattended without loading any additional chunks. The physical
active contract is also kept out of the maid's own inventory, cursor and external
container slots while the home is active; menu-close handling is a second safety
boundary, not the only one. If the carrier itself is destroyed in the interior,
the existing TLM emergency resurrection-film format is used and film delivery is
committed before the live maid is discarded. On return the current slot is
resolved directly, preserving a valid same-slot selection. Twelve hours away
means one resolution, not twelve hours of simulation.

## Verification

`python tools/validate_home_life.py` compiles/runs pure-Java boundary and resolver
checks plus architecture tripwires. Existing validators remain in Core CI.
`gradle clean build -Pci=true` is the full build gate.

The existing headless preview workflow now includes the pinned TLM runtime and
an explicit `contractinterior validate-home` fixture (available only with
`-PgalleryPreview=true`). It checks plot NBT roundtrip/isolation, real bed/chair/board-game APIs,
target invalidation, managed Joy/picnic schedule bridging, native Home Meal consumption, and restoration of the same maid's brain/task/schedule. Preview still
uses real Forge saves, `save-all flush` and `.mca` rendering for Sakura 1–5 and
all five maximum-stage themes. These server checks verify state and APIs;
client animation appearance and complex player-built routes still need in-game
acceptance testing.


## Interior containment

An active home session is bound to its own generated plot footprint. Cross-dimension
travel is handled by the normal lifecycle event, while same-dimension teleports
(such as a waystone inside the shared Contract Interior dimension) are checked on
the regular safety tick. A teleport outside the active binding's plot returns the
owner to that plot's origin instead of exposing another binding's cell.

The **resident maid** is contained by the same horizontal footprint. Home Life
normally never paths outside it; if combat AI, a teleporting mod or another force
moves the live resident outside, the safety guard ends the stale furniture pose
and returns her to the binding origin only when that origin chunk is already
loaded. It never creates a chunk ticket merely to recover an unattended resident;
otherwise she is paused until recovery is safe. Both checks are horizontal, so
vertical player builds inside the owned footprint remain usable.
