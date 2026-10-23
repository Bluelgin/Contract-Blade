package com.maidweapon.forge.system.danmaku;

/** Bound both visible entities and delayed shots in multiplayer spell spam. */
public final class DanmakuPerformanceBudget {
    public static final int MAX_ACTIVE_PER_OWNER = 96;
    public static final int MAX_QUEUED_PER_OWNER = 128;
    public static final int MAX_QUEUED_GLOBAL = 1536;
    public static final int MAX_WAVE_ENTITIES = 32;
    /** Gameplay has no traditional cooldown. This protects one packet per server tick. */
    public static final int MAX_ACTIVATIONS_PER_TICK = 1;

    public static boolean capacity(int active, int pending, int incoming) {
        return active >= 0 && pending >= 0 && incoming > 0
                && incoming <= MAX_WAVE_ENTITIES
                && active + pending + incoming <= MAX_ACTIVE_PER_OWNER
                && pending + incoming <= MAX_QUEUED_PER_OWNER;
    }

    private DanmakuPerformanceBudget() { }
}
