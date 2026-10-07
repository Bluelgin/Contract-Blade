package com.maidweapon.forge.system.fox.challenge;

/** One tick timeline shared by server choreography and client sampling. Never uses packet-arrival time. */
public final class BlackFoxDimensionTimeline {
    public static final int DIVE_END = 8, WARNING = 42, EMERGE = 50, LOCK = 60, HIT = 64, END = 84;
    public static final int COOLDOWN = 180;
    public static float progress(float age, int start, int end) {
        float t = Math.max(0, Math.min(1, (age-start)/(end-start)));
        return t*t*(3-2*t);
    }
    public static float bodyAlpha(float age) {
        if (age < DIVE_END) return 1-progress(age, 0, DIVE_END);
        if (age < EMERGE) return 0;
        return progress(age, EMERGE, EMERGE+2);
    }
    public static float slashAlpha(float age) {
        return age < HIT ? 0 : 1-progress(age, HIT+2, HIT+14);
    }
    public static boolean submerged(int age) { return age >= DIVE_END && age < EMERGE; }
    private BlackFoxDimensionTimeline() { }
}
