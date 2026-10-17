package com.maidweapon.forge.system.resonance;

/** Pure cost decision; waivers never grant authority or bypass non-resource requirements. */
public final class ResonanceCostPolicy {
    public enum Payment { PAID, WAIVED, INSUFFICIENT, UNAUTHORIZED, ALREADY_PAID, INVALID_COST }
    public static Payment evaluate(boolean authorized, boolean waived, int available, int cost) {
        if (!authorized) return Payment.UNAUTHORIZED;
        if (cost < 0) return Payment.INVALID_COST;
        if (waived) return Payment.WAIVED;
        return available >= cost ? Payment.PAID : Payment.INSUFFICIENT;
    }
    private ResonanceCostPolicy() { }
}
