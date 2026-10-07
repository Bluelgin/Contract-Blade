# Black Fox grand slash

The existing slash mask channels are separated into a white-pink luminous core, a violet cut ribbon, a soft surrounding halo and sparse drifting residual streaks. The shared shader selects these using per-quad kinds 2–5; legacy kinds 0/1 remain unchanged. No new texture or effect entity is needed.

`BlackFoxSlashTimeline` owns only visual lifetime: instant appearance at tick 64; core ends after 4 ticks (0.2s), ribbon after 9 (0.45s), halo after 12 (0.6s), remnants after 14 (0.7s). Each has a separate brief hold, brightness envelope and head-to-tail erasure. The pointed +X end disappears first. The curved silhouette is preserved without squeezing texture columns into kinks. Damage and counter timing remain owned by the existing server skill.

`BlackFoxEffectQuad.sweep` samples 32 strips into the existing render batches. Both fullbright fallback texture and additive shader glow use the same geometric visibility mask. Texture padding is accounted for when mapping the glow mask. Effects use the synchronized skill clock, without spawning entities or accumulating frame state. Leaving the skill immediately removes the effect.

The opt-in native client fixture renders 33 actual GPU frames and verifies that the head disappears before the tail, brightness decreases, and the effect is gone at its terminal time. Pure timeline tests also check each layer's monotonic decay and distinct end time. Preview output: `art/black_fox/dimension_strike/slash-layered-decay.gif`.
