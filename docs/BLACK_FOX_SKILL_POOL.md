# Phase-one skill pool

`BlackFoxSkillPool` is encounter-local, pure scheduling state. `BlackFoxFight.start` commits cooldowns, while selection alone does not. Time is a monotonic encounter tick counter, so changing skill/entering stagger does not reset cooldowns. Each skill has its own ready timestamp; no per-tick cooldown-array scan is needed.

The weighted bag has eleven ordinary-distance seats: three melee links, two dimension strikes, two rift crosses, three arc barrages and one standing iaido. Additional context-only seats are distant phantom feint/seal, airborne sky cut and crowded sidestep. Choose randomly among eligible unused seats, excluding the last committed pool skill and consecutive standing/dimensional grand slashes. If remaining seats are unavailable, refresh the eligible round; if all are cooling down, remain ready rather than bypass cooldowns. Range/height still gate inappropriate melee and aerial attacks.

Cooldowns begin on entry and include execution/recovery: probe 55 ticks, cross 80, native B/feint/sky/sidestep 100, rift cross 130, seal 140, arc barrage/dimension strike 180. Interrupted attacks still spend cooldown. Reactive probe follow-ups check the same pool; defensive counter-cut retains its separate defense cooldown. Pressure-triggered unseal and second-phase arena choreography remain outside the ordinary attack pool.

Weighted seats influence selection opportunities, not guaranteed final cast percentages: distance, height, cooldown and interruptions also matter. No skill executors or hit/parry windows were moved into the scheduler. New skills can be added with one seat definition, cooldown and optional eligibility condition.

`tools/validate_black_fox_new_skills.py` includes 100-seed simulations checking cast variety, no consecutive repeats, exact cooldown boundaries, cooldown saturation waiting and independent encounter state. Native server fixtures continue to exercise actual attacks and counters.
