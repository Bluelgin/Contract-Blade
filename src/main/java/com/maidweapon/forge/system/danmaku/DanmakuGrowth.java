package com.maidweapon.forge.system.danmaku;

/** Fixed-strength weapon enhancement; mastery arguments remain for binary/source compatibility only. */
public final class DanmakuGrowth {
    public record AutoAttack(int count, double bonusFraction, int cooldownTicks) { }

    public static AutoAttack auto(int mastery) { return auto(mastery, false); }
    public static AutoAttack auto(int mastery, boolean consecutive) {
        int count = consecutive ? 6 : 4;
        return new AutoAttack(count, count * .03, 5);
    }

    /** Theoretical per-target fraction when every independent shot connects. */
    public static double fraction(DanmakuForm form, int mastery) {
        return switch (form) {
            case NEEDLE -> .60;
            case FLOWER -> 24.0 * .035;
            case GUARD -> .20;
            case FANTASY -> 1.60;
        };
    }

    public static int count(DanmakuForm form, int mastery) {
        return switch (form) {
            case NEEDLE -> 12; // two volleys, six independently damaging needles each
            case FLOWER -> 12; // per wave: 24 shots total, fixed
            case GUARD -> 6; // fixed interception charges
            case FANTASY -> 32; // four phases, eight per phase
        };
    }

    /** One source of truth for wave timing and legal phase indices. */
    public static int waveCount(DanmakuForm form) {
        return switch (form) {
            case NEEDLE, FLOWER -> 2;
            case GUARD -> 0;
            case FANTASY -> 4;
        };
    }
    public static boolean waveAllowed(DanmakuForm form, int mastery, int index) {
        return form != null && index >= 0
                && index < waveCount(form);
    }

    public static int waveSpacing(DanmakuForm form) {
        return form == DanmakuForm.FLOWER ? 13 : form == DanmakuForm.NEEDLE ? 13
                : form == DanmakuForm.FANTASY ? 14 : 0;
    }

    public static int maxProjectilesPerCast(DanmakuForm form, int mastery) {
        return switch (form) {
            case NEEDLE -> 12;
            case FLOWER -> count(form, mastery) * 2;
            case GUARD -> 8;
            case FANTASY -> 32;
        };
    }

    /** Actual active-skill cost in the server-owned resonance pool. */
    public static int resonanceCost(DanmakuForm form, int mastery) {
        return DanmakuResonanceCostPolicy.cost(form);
    }
    private DanmakuGrowth() { }
}
