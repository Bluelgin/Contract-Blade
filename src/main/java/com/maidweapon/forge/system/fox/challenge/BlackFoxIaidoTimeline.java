package com.maidweapon.forge.system.fox.challenge;

/** Presentation samples the existing strike clock; never changes its damage or parry timing. */
public final class BlackFoxIaidoTimeline {
    public static final int CHARGE = BlackFoxDimensionTimeline.EMERGE;
    public static final int DRAW = BlackFoxDimensionTimeline.HIT - 2;
    public static final int HIT = BlackFoxDimensionTimeline.HIT;
    public static final int END = BlackFoxDimensionTimeline.END;

    public static boolean charging(float age) { return age >= CHARGE && age < DRAW; }
    public static boolean drawn(float age) { return age >= DRAW && age < END - 3; }
    public static float progress(float age) { return clamp((age - CHARGE) / (DRAW - CHARGE)); }
    public static float outerAlpha(float age) {
        if (!charging(age)) return 0;
        return Math.min(1, (age - CHARGE) / 2) * (1 - clamp((age - (DRAW - 3)) / 3));
    }
    public static float coreAlpha(float age) {
        return charging(age) ? Math.min(1, (age - CHARGE) / 2) * (.35f + .60f * progress(age)) : 0;
    }
    public static float animationTick(float age) {
        if (age <= CHARGE) return 6; // Already posed on emergence, not a neutral pose pop.
        if (age < DRAW) return mix(6, 26.4f, (age - CHARGE) / (DRAW - CHARGE));
        if (age < HIT) return mix(26.4f, 28, (age - DRAW) / (HIT - DRAW));
        if (age < HIT + 8) return mix(28, 36.4f, (age - HIT) / 8);
        return mix(36.4f, 56, (age - HIT - 8) / (END - HIT - 8));
    }
    /** Head moves from the periphery to the mouth, accelerating inward. */
    public static float radius(float cycle, float reach) { return reach * (1 - clamp(cycle) * clamp(cycle)); }
    private static float clamp(float value) { return Math.max(0, Math.min(1, value)); }
    private static float mix(float a, float b, float t) { return a + (b - a) * clamp(t); }
    private BlackFoxIaidoTimeline() { }
}
