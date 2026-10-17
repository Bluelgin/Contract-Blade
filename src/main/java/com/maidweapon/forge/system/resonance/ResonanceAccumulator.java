package com.maidweapon.forge.system.resonance;

/** Per-contract session remainder. Never derives rewards from elapsed/offline wall time. */
public final class ResonanceAccumulator {
    private long lastTick = Long.MIN_VALUE;
    private long remainder;
    public int tick(long now, long units, int current, int maximum) {
        if (now == lastTick) return 0;
        lastTick = now;
        if (current >= maximum) { remainder = 0; return 0; }
        remainder += Math.max(0, units);
        int restored = (int) Math.min(maximum - current, remainder / ResonanceRecoveryPolicy.UNIT);
        remainder -= restored * ResonanceRecoveryPolicy.UNIT;
        if (current + restored >= maximum) remainder = 0;
        return restored;
    }
}
