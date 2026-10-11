package com.maidweapon.forge.system.fox.challenge;

/** Pure victory-exit clock. A failed companion recall retries without restarting the cinematic. */
public final class FoxChallengeCollapseTimeline {
    public static final int DURATION = 200, RETRY_INTERVAL = 20;
    public static int seconds(int remaining) { return Math.max(0, (remaining + 19) / 20); }
    public static float progress(int remaining) { return Math.max(0, Math.min(1, 1 - remaining / (float)DURATION)); }
    public static int particleInterval(int remaining) { return remaining > 60 ? 8 : 4; }
    private FoxChallengeCollapseTimeline() { }
}
