package com.maidweapon.forge.system.resonance;

/** Integer micro-resonance; one tick at the default minimum rate is exact. */
public final class ResonanceRecoveryPolicy {
    public static final long UNIT = 1_000_000;
    /** Presence is independent of activity priority: stale ability reports cannot erase live recovery. */
    public static double rate(boolean manifested, ResonanceActivity activity, double stored, double liveIdle,
                              double abilityIdle, double storedCombat, double liveCombat) {
        boolean combat = activity == ResonanceActivity.COMBAT || activity == ResonanceActivity.ABILITY_ATTACKING
                || activity == ResonanceActivity.BERSERK;
        if (manifested) return combat ? liveCombat : liveIdle;
        return combat ? storedCombat : activity == ResonanceActivity.ABILITY_IDLE ? abilityIdle : stored;
    }
    public static long perTick(double perSecond, double multiplier) {
        if (!Double.isFinite(perSecond) || !Double.isFinite(multiplier) || perSecond < 0 || multiplier < 0)
            throw new IllegalArgumentException("Invalid recovery rate");
        return Math.round(perSecond * multiplier * UNIT / 20);
    }
    public static int nextStage(int current, int requested, int maximum) {
        return current >= 0 && current < maximum && requested == current + 1 ? requested : current;
    }
    private ResonanceRecoveryPolicy() { }
}
