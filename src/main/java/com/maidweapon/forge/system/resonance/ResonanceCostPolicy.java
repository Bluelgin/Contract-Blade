package com.maidweapon.forge.system.resonance;

/** Pure cost decision; waivers never grant authority or bypass non-resource requirements. */
public final class ResonanceCostPolicy {
    public enum Payment { PAID, WAIVED, INSUFFICIENT, UNAUTHORIZED, ALREADY_PAID, INVALID_COST }
    public static Payment evaluate(boolean authorized, boolean waived, int available, int cost) {
        return Payment.valueOf(com.maidweapon.port.domain.resonance.ResonanceRules
                .evaluate(authorized, waived, available, cost).name());
    }
    private ResonanceCostPolicy() { }
}
