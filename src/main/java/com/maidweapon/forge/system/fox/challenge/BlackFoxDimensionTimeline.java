package com.maidweapon.forge.system.fox.challenge;

/** One tick timeline shared by server choreography and client sampling. Never uses packet-arrival time. */
public final class BlackFoxDimensionTimeline {
    public static final int DIVE_END = 4, WARNING = 8, EMERGE = 16, LOCK = 20, HIT = 24;
    public static final int ROUND = 34, ROUNDS = 3, FINAL_ROUND = 44, END = ROUND * 2 + FINAL_ROUND;
    public static final int COOLDOWN = 180;
    public static float localAge(float age) {
        if (age <= 0) return 0;
        if (age >= ROUND * 2 + 1) return Math.min(FINAL_ROUND, age - ROUND * 2);
        return (age - 1) % ROUND + 1;
    }
    public static boolean heavy(float age) { return age >= ROUND * 2 + 1; }
    public static int hitAge(float age) { return heavy(age) ? HIT + 6 : HIT; }
    public static int lockAge(float age) { return heavy(age) ? LOCK + 6 : LOCK; }
    /** Map each fast round onto the established iaido animation; standing iaido stays unchanged. */
    public static float poseAge(float age) {
        float local = localAge(age);
        int hit = hitAge(age), end = heavy(age) ? FINAL_ROUND : ROUND;
        if (local < EMERGE) return BlackFoxIaidoTimeline.CHARGE;
        if (local < hit) return BlackFoxIaidoTimeline.CHARGE
                + (local - EMERGE) / (hit - EMERGE) * (BlackFoxIaidoTimeline.HIT - BlackFoxIaidoTimeline.CHARGE);
        return BlackFoxIaidoTimeline.HIT + (local - hit) / (end - hit)
                * (BlackFoxIaidoTimeline.END - BlackFoxIaidoTimeline.HIT);
    }
    public static float progress(float age, int start, int end) {
        float t = Math.max(0, Math.min(1, (age-start)/(end-start)));
        return t*t*(3-2*t);
    }
    public static float bodyAlpha(float age) {
        age = localAge(age);
        if (age < DIVE_END) return 1-progress(age, 0, DIVE_END);
        if (age < EMERGE) return 0;
        return progress(age, EMERGE, EMERGE+2);
    }
    public static float slashAlpha(float age) {
        int hit = hitAge(age);
        age = localAge(age);
        return age < hit ? 0 : 1-progress(age, hit+2, hit+14);
    }
    public static boolean submerged(int age) {
        float local = localAge(age);
        return age < END && local >= DIVE_END && local < EMERGE;
    }
    private BlackFoxDimensionTimeline() { }
}
