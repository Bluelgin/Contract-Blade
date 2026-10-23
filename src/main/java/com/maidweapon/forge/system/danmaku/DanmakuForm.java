package com.maidweapon.forge.system.danmaku;

/**
 * Brief server-side intervals prevent spam; network input is additionally rate-limited by
 * one server tick, server resonance and projectile/queue caps independently.
 */
public enum DanmakuForm {
    NEEDLE(3, 8), FLOWER(5, 12), GUARD(6, 10), FANTASY(10, 20);
    private final int requiredLevel, cooldownTicks;
    DanmakuForm(int requiredLevel, int cooldownTicks) {
        this.requiredLevel = requiredLevel;
        this.cooldownTicks = cooldownTicks;
    }
    /** Legacy field retained only for compatibility; forms never have an unlock gate. */
    public int requiredLevel() { return 0; }
    public int cooldownTicks() { return cooldownTicks; }
    public boolean unlocked(int mastery) { return true; }
    public static DanmakuForm safe(int id) {
        return id < 0 || id >= values().length ? null : values()[id];
    }
}
