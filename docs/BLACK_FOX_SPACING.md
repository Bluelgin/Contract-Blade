# Readable first-phase engagement spacing

The existing spacing policy, blink executor and phase-one dispatcher own this behavior; no new AI/controller is added.

Normal blink landings target 4.5 blocks (accepted band 4–5), inside the six-block ordinary-cut reach. Adjustment triggers only below three or above seven horizontal blocks. This hysteresis avoids reacting to every step. Landing candidates start in the player's facing direction and use bounded alternatives up to 55 degrees to either side; clamped points must remain forward, within the landing band and collision-free. If all forward candidates are blocked, the boss retries later rather than landing behind the player.

Both ordinary repositioning and skill sidesteps share the existing forty-tick blink cooldown, eight-tick warning and eight-tick arrival recovery. Recheck separation/collision at arrival; cancel a stale landing if the player moved too close or too far. Distance maintenance runs only in first-phase APPROACH, not during windup/cuts, second-phase central attacks or dimensional emergence. Failed/cooling requests do not permanently stall skill selection.

A successful parry cancels pending blink and holds approach/next attack selection for at least twelve ticks, also preventing skill blink requests during that feedback interval. Existing longer stagger and native Combo B behavior remain intact. No continuous travel, added packets, entities, inventory scans or per-frame client tracker.

Pure tests cover threshold hysteresis and attack reach. Native server regression checks close/far landings, forward placement at arena edges, shared sidestep cooldown, interrupted blink, real parry feedback hold and existing combat mechanics.
