package com.maidweapon.forge.system.oath;

/** Nano-unit fixed point; fractional consumption survives ticks and save boundaries. */
public final class OathBurn {
    public static final long SCALE = 1_000_000_000;
    public record Step(int cost, long remainder) { }
    public static Step tick(int maximum, double fractionPerSecond, long remainder) {
        long nano = Math.round(maximum * fractionPerSecond * SCALE / 20.0);
        long total = Math.max(0, remainder) + Math.max(0, nano);
        return new Step((int) Math.min(Integer.MAX_VALUE, total / SCALE), total % SCALE);
    }
    public static int penaltyLevels(int amplifier) { return Math.min(5, Math.max(0, amplifier + 1)); }
    private OathBurn() { }
}
