package com.maidweapon.forge.system.resonance;

/** Integer micro-resonance; one tick at the default minimum rate is exact. */
public final class ResonanceRecoveryPolicy {
    public static final long UNIT = 1_000_000;
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
