package com.maidweapon.port.domain.resonance;

/** Transient fractional recovery; one observed tick, never elapsed/offline rewards. */
public final class ResonanceAccumulator {
    private long lastTick = Long.MIN_VALUE;
    private long remainder;
    public int tick(long now, long units, int current, int maximum) {
        if (now < 0 || current < 0 || maximum < 1 || current > maximum || units < 0)
            throw new IllegalArgumentException("Invalid recovery input");
        if (now <= lastTick) return 0;
        lastTick = now;
        if (current == maximum) { remainder = 0; return 0; }
        long cap = (long)(maximum-current)*ResonanceRules.UNIT;
        remainder = Math.min(cap, remainder);
        remainder += Math.min(units, cap-remainder);
        int restored = (int)(remainder/ResonanceRules.UNIT);
        remainder -= restored*ResonanceRules.UNIT;
        if (current+restored == maximum) remainder=0;
        return restored;
    }
}
