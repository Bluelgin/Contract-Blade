package com.maidweapon.forge.system.danmaku;

/** Global ContractMastery gates ordinary-contract talisman abilities, not the talisman ItemStack. */
public final class DanmakuGrowth {
    public record AutoAttack(int count, double bonusFraction, int cooldownTicks) { }
    public static AutoAttack auto(int mastery) { return auto(mastery, false); }
    public static AutoAttack auto(int mastery, boolean consecutive) {
        int level = Math.max(1, Math.min(10, mastery));
        return new AutoAttack(level >= 8 && consecutive ? 4 : level >= 2 ? 3 : 2,
                Math.min(0.20, 0.15 + (level - 1) * 0.005), 120);
    }
    /** Per array total, not per bullet. Bullets cannot bypass ordinary hit limitations. */
    public static double fraction(DanmakuForm form, int mastery) {
        return switch (form) {
            case NEEDLE -> 0.20;
            case FLOWER -> mastery >= 9 ? 0.28 : 0.20;
            case GUARD -> mastery >= 6 ? 0.12 : 0.08;
            case FANTASY -> 0.55;
        };
    }
    public static int count(DanmakuForm form, int mastery) {
        return switch (form) {
            case NEEDLE -> 5;
            case FLOWER -> mastery >= 9 ? 12 : mastery >= 7 ? 9 : 7;
            case GUARD -> mastery >= 6 ? 8 : 5;
            case FANTASY -> 24;
        };
    }
    /** No resource withdrawal in this PR; a future skill-cost policy can replace this safely. */
    public static int resonanceCost(DanmakuForm form, int mastery) { return 0; }
    private DanmakuGrowth() { }
}
