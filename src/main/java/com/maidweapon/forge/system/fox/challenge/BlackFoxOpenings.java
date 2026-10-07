package com.maidweapon.forge.system.fox.challenge;

/** Visible preparation/recovery windows: a normal successful hit is enough, no special input. */
public final class BlackFoxOpenings {
    public static boolean active(BlackFoxFight.Skill skill, int age) {
        return switch (skill) {
            case RIFT_CROSS -> age >= 8 && age < 24 || age >= 44 && age < 64;
            case ARC_BARRAGE -> age >= 10 && age < 18 || age >= 48 && age < 50 || age >= 78 && age < 94;
            case DIMENSION_STRIKE -> age >= BlackFoxDimensionTimeline.HIT + 4 && age < BlackFoxDimensionTimeline.END;
            case SWORD_WHEEL -> age >= 60 && age < 92;
            case DOMAIN_CROSS -> age >= 52 && age < 88;
            case DOMAIN_RAIN -> age >= 40 && age < 70;
            case DOMAIN_SEAL -> age >= 84 && age < 110;
            default -> false;
        };
    }
    private BlackFoxOpenings() { }
}
