package com.maidweapon.port.domain.resonance;

/** Pure gameplay policy, independent of Minecraft and item storage. */
public record ResonanceRules(int maximum, int legacyMaximum, double stored, double liveIdle,
                             double abilityIdle, double storedCombat, double liveCombat) {
    public static final ResonanceRules DEFAULT = new ResonanceRules(1000, 200, .5, 4, .5, 0, 2);
    public static final long UNIT = 1_000_000;
    public enum Activity { STORED, MANIFESTED_IDLE, ABILITY_IDLE, COMBAT, ABILITY_ATTACKING, BERSERK }
    public enum Payment { PAID, WAIVED, INSUFFICIENT, UNAUTHORIZED, ALREADY_PAID, INVALID_COST }
    public ResonanceRules {
        if (maximum < 1 || maximum > 1_000_000 || legacyMaximum < 1 || legacyMaximum > 1_000_000)
            throw new IllegalArgumentException("Invalid pool maximum");
        for (double rate : new double[]{stored, liveIdle, abilityIdle, storedCombat, liveCombat})
            if (!Double.isFinite(rate) || rate < 0 || rate > 200)
                throw new IllegalArgumentException("Invalid rate");
    }
    public static Payment evaluate(boolean authorized, boolean waived, int available, int cost) {
        if (!authorized) return Payment.UNAUTHORIZED;
        if (cost < 0) return Payment.INVALID_COST;
        if (waived) return Payment.WAIVED;
        return available >= cost ? Payment.PAID : Payment.INSUFFICIENT;
    }
    public int legacyUnits(int value) {
        return (int)Math.min(Integer.MAX_VALUE, Math.round(Math.max(0, value) * ((double)maximum/legacyMaximum)));
    }
    public static int convertLegacy(int value, int previousMaximum, int newMaximum) {
        if (previousMaximum < 1 || newMaximum < 1) throw new IllegalArgumentException("Invalid maximum");
        long bounded = Math.max(0L, Math.min((long)value, previousMaximum));
        return (int)Math.min(newMaximum, (bounded * newMaximum + previousMaximum/2L)/previousMaximum);
    }
    public long recoveryUnits(boolean manifested, Activity activity, double multiplier) {
        if (activity == null || !Double.isFinite(multiplier) || multiplier < 1 || multiplier > 100)
            throw new IllegalArgumentException("Invalid recovery input");
        boolean combat = activity == Activity.COMBAT || activity == Activity.ABILITY_ATTACKING || activity == Activity.BERSERK;
        double rate = manifested ? (combat ? liveCombat : liveIdle)
            : combat ? storedCombat : activity == Activity.ABILITY_IDLE ? abilityIdle : stored;
        return Math.round(rate * multiplier * ((double)maximum/legacyMaximum) * UNIT / 20);
    }
}
