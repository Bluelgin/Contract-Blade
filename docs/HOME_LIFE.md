# Contract Home Life

## Ownership boundary

Contract Home Life keeps the original TLM maid and the existing contract lifecycle.
It does not create a second entity, inventory, renderer or storage authority.

- Online walking, pathfinding, furniture choices, meals, sleep and emergency behavior belong to TLM's native brain.
- Contract Blade configures a temporary idle/home scope and native DAY schedule on entry. It records and restores the original task, schedule, sitting state and schedule positions on exit or capture.
- Default Minecraft time does not replace the native schedule or force IDLE/REST each tick.
- Explicit realtime/server clock settings remain backward-compatible. Only those opt-in modes bridge the personal clock on phase changes.
- If native setup fails, original settings are restored. There is no custom online AI fallback.
- An observation snapshot is recorded at most every 100 ticks; it does not issue walking targets or rebuild the brain.

## Arrival and offline state

The resident checkpoint is restored before spawning the same maid. A short absence preserves the previous activity when it is still safe and appropriate. A longer absence uses bounded, deterministic arrival inference. No offline entity simulation or forced chunk tickets are used.

Arrival staging uses native beds, chairs, bookshelves, computers and keyboards. It checks ownership, loaded furniture, occupancy, a collision-free standing position and a reachable path, with at most four candidates. Invalid furniture falls back to the saved checkpoint, never the player's entrance. No furniture leaves ordinary roaming to TLM.

Freshly spawned residents wait two ticks for registration before staging. The selected seat/sleep pose is retained during the handoff rather than immediately cancelled. After that one-shot scene, Contract Blade never periodically picks another online activity.

Board-game and picnic adapters are read-only activity observers. They do not switch the task, spawn seats, mount the maid or consume food. Native furniture gameplay remains controlled by TLM.

## Safety and persistence

- The home marker is binding-specific. Plot escape is recovered only using already loaded home chunks; unattended residents cannot force-load the plot.
- On exit, the last activity and resident location are recorded before releasing the temporary settings.
- Failed capture or restart leaves an unattended resident paused until a valid owner visit resumes her.
- Original NoAI settings are respected.
- Contract/inventory protection and equipment restrictions are unchanged: no projection inside the home, no weapon/armor transfers into the resident's protected inventory.
- The same contract lifecycle stores UUID, model, equipment, inventory and progression. Home state records location/activity only, not a duplicate maid.
- Existing HomeLife NBT and clock commands remain readable. Legacy temporary board/seat markers are removed by restoration.
- Mouse pickup, creative cursor movement and container-close rescue retain the physical contract; no container action is permission to create another resident.

## Clock commands

Inside your own home:

- `/contractinterior clock minecraft`: default Minecraft time.
- `/contractinterior clock realtime`: explicitly opt into personal real-world time.
- `/contractinterior clock server`: explicitly opt into server timezone time.
- `/contractinterior timezone <zone>`: adjust the custom clock timezone.

Custom sky packets do not mutate the shared dimension's world time. Native Minecraft-mode AI uses the actual world day time, which shares the overworld clock.

## Verification

`python tools/validate_home_life.py` tests clock/arrival inference and architecture boundaries, including the absence of the legacy online scheduler and custom HOME brain.
The explicit `contractinterior validate-home` fixture is available only with `-PgalleryPreview=true -PhomeLifeTest=true`.
It verifies native CORE/furniture/wander/bed goals, unchanged native schedule and brain during online observation, arrival poses, missing/occupied furniture, UUID continuity, pause/resume, containment and restoration of the external task/schedule.
It also runs contract carrier and equipment/lifecycle safety fixtures.

Use an isolated test instance and save. Server state/API tests do not substitute for client animation and complex player-built route testing.
