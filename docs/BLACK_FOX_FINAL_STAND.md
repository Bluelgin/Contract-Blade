# Balance loss and final stand

Development-only encounter changes; the stable public package is unchanged.

## Balance

`BlackFoxBalance` counts successful player parries and native Combo B clashes through the existing confirmed feedback path, not incoming projectile contacts. Five confirmations trigger a five-second (100-tick) balance loss and reset the count. The same consumed swing cannot count twice; confirmations within four ticks are deduplicated. Successful parries during balance loss do not build another reward.

The controller consumes the pending reward after attack execution, so an executor's ordinary short stagger cannot overwrite it. Pending native B clashes apply it on the following combat tick. It reuses the existing stagger pose, stops attacks, cancels pending movement and clears ranged projectiles. Each accepted damaging hit advances recovery by ten ticks (0.5 seconds); blocked hits do not. Existing eight-tick opening damage cadence and vanilla hit immunity remain in force.

## Terminal black hole

At 10% remaining health, or on an otherwise lethal hit, the controller enters the final stand rather than vanilla death. A lethal first-phase burst also enters this state, so it cannot skip the core. Normal phase-two domain combat no longer schedules recurring black holes.

During final stand, boss damage is rejected. The core descends over 300 ticks and remains targetable after reaching the arena floor; damage pulses continue every forty ticks until the core is broken. Landing is not a victory condition. Unexpected core removal/spawn failure causes a twenty-tick retry, not victory. Only the owned core's real defeat calls `finishDefeat`, clears hazards and records the existing shrine return/victory. Session retirement on player death, departure or disconnect still cleans all entities normally.

Core attacks emit four alternating native phantom swords/blades every ten ticks, aimed at the challenger's current eye position with a small horizontal spread. Speeds are 1.35/1.1; existing sixty-tick expiry, sixty-projectile cap and once-per-world-tick idempotence remain. Absorption during standing iaido is cosmetic, using 24 recycled visual slots rather than an unbounded swarm of projectile entities.

## Validation

Native server fixtures exercise five fresh parries, duplicate rejection, 100-tick loss of balance, accepted-hit acceleration, healthy phase two without premature final stand, invulnerability, core lock-on/phantom-sword break, landing without auto-completion, unexpected-removal retry, aimed projectile motion, bounded 1200-tick emission and lethal-hit interception. Pure timing tests and native GPU charge/arena-slash tests also run.
