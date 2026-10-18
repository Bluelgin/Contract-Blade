package com.maidweapon.forge.system.danmaku;

/** All per-level damage budgets, cooldowns and bullet caps live here. */
public final class DanmakuGrowth {
    public record AutoAttack(int count, double bonusFraction, int cooldownTicks) { }

    public static AutoAttack auto(int mastery) { return auto(mastery, false); }
    public static AutoAttack auto(int mastery, boolean consecutive) {
        int lv = Math.max(1, Math.min(10, mastery));
        int count = lv >= 8 && consecutive ? 4 : lv >= 2 ? 3 : 2;
        return new AutoAttack(count, Math.min(.20, .10 + lv * .009), 40);
    }

    /** Whole spell damage per TARGET, paid only for valid hits and spread into timed waves. */
    public static double fraction(DanmakuForm form, int mastery) {
        return switch (form) {
            case NEEDLE -> .45;
            case FLOWER -> .35;
            case GUARD -> mastery >= 6 ? .20 : .12;
            case FANTASY -> 1.20;
        };
    }

    public static int count(DanmakuForm form, int mastery) {
        return switch (form) {
            case NEEDLE -> 10; // two volleys, each 5 needles
            case FLOWER -> mastery >= 9 ? 12 : mastery >= 7 ? 9 : 7; // per wave
            case GUARD -> mastery >= 6 ? 8 : 5; // intercept charges
            case FANTASY -> 24; // three phases, eight per phase
        };
    }

    public static int waveSpacing(DanmakuForm form) {
        return form == DanmakuForm.FLOWER ? 13 : form == DanmakuForm.NEEDLE ? 13
                : form == DanmakuForm.FANTASY ? 14 : 0;
    }

    public static int maxProjectilesPerCast(DanmakuForm form, int mastery) {
        return switch (form) {
            case NEEDLE -> 10;
            case FLOWER -> count(form, mastery) * 2;
            case GUARD -> 8;
            case FANTASY -> 24;
        };
    }

    /** Deferred: no resonance cost until a unified skill payment policy is approved. */
    public static int resonanceCost(DanmakuForm form, int mastery) { return 0; }
    private DanmakuGrowth() { }
}
