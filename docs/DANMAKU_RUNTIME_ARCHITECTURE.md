# Contract Blade: Danmaku Runtime Architecture (PR #27)

## Intent and invariants

This is a responsibility and performance refactor, **not a balance/content change**.

- Keep the same four active forms, costs, level gates, timings, damage calculations, target protection, and normal-attack coordination.
- The client submits only a form or air-swing request. The authoritative Minecraft server thread re-checks current carrier, contract owner/binding, original TLM accessory, mastery, funding, and targets.
- Do not acquire a cross-tick authorization token. Deferred waves and real projectile collisions must reauthorize the currently equipped contract; failed or stale checks deny the effect.
- Never weaken other mods' invulnerability windows, protected Boss stages, soul-damage proof requirements, or legitimate TLM maid inventory checks.

## Responsibilities

| Component | Sole responsibility |
| --- | --- |
| `DanmakuCombatService` | Forge event orchestration, cost-before-effect and cleanup dispatch |
| `DanmakuInputGate` | Per-player, per-form cast/attack tick barriers |
| `DanmakuWaveSessions` | Consecutive melee coordination and delayed wave lifecycle |
| `DanmakuGuardService` | Guard session, legitimate hostile-shot interception, retaliation |
| `DanmakuTargeting` | Reusable targeting and held-weapon attack snapshot |
| `DanmakuEligibility` | Server-side ordinary-contract and native-bauble authorization |
| `DanmakuRuntimeApi` | Fail-closed adapter boundary, without TLM or domain implementation imports |
| `DanmakuRuntimeRules` | Registration adapter from core gameplay policy to the boundary |
| `DanmakuShotQueue` | Native TLM entity scheduling and per-owner projectile count |
| `DanmakuActiveCounter` | Same-server-tick owner/world entity-count snapshot |
| `DanmakuPacketLimiter` | Per-player 50 ms inbound packet gate, invoked **inside** `enqueueWork` |

Dependency direction: the gameplay orchestration can request native shots via `compat/tlm/danmaku`. Native projectile adapters call only `forge/api/danmaku`, **not** back into `system/danmaku` implementations. The bootstrap wires one concrete `DanmakuRuntimeRules` provider. Missing provider means no permission/damage.

## Threading, memory and budgets

- Packet limiter, state maps, cast/wave queues and entity-count snapshots are only read or mutated on the server's main thread; the network dispatcher enqueues work first.
- Queued shots are bounded globally and by owner; deferred shots recheck original maid UUID and dimension before spawning.
- `DanmakuActiveCounter` memoizes one nearby-owned-entity count per player, dimension and server tick. Successfully spawned projectiles increment the cached count during that tick. A projectile disappearing early may conservatively overcount until the next tick; it cannot grant extra capacity.
- Logout and server stop purge owner maps and counter snapshots; clocks running backwards invalidate the snapshot.
- The server still performs a full authorization for every projectile/ability effect. No expensive contract-identity cache is added because inventory changes can invalidate it within a tick.

## Verification

- `DanmakuPacketLimiterTest`: independent senders, 50 ms boundary, duplicates, disconnect/reset, backward clock.
- `DanmakuActiveCounterTest`: burst increments, tick/dimension invalidation, logout cleanup.
- `validate_architecture.py`: native queue and projectile may not directly import the gameplay subsystem.
- Core CI must pass Java 17 compilation, all existing tests and the original Forge/TLM native fixtures.
- In a real client: rapid left-clicks, melee-to-auto salvos, 4 active forms, timed final wave, guard projectile interception, missing/removed accessory, 2 players, weapon swap, world change, death and relogin.
- Profile multiplayer TPS at 2, 8, 16 players with simultaneous salvos; synthetic tests are **not** a substitute for that real-client measurement.

## Non-goals

This pass does not rewrite the contract lifecycle, resonance SavedData, Akatsuki soul engine, or other optional Boss adapters. Those require separate bounded and verified changes if an actual issue emerges.
