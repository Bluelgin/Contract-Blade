package com.maidweapon.forge.system.danmaku;

/**
 * Traditional ability cooldowns are removed. Network input is rate-limited by
 * one server tick, server resonance and projectile/queue caps independently.
 */
public enum DanmakuForm {
    NEEDLE(5, 0), FLOWER(5, 0), GUARD(5, 0), FANTASY(10, 0);
    private final int requiredLevel, cooldownTicks;
    DanmakuForm(int requiredLevel, int cooldownTicks) {
        this.requiredLevel = requiredLevel;
        this.cooldownTicks = cooldownTicks;
    }
    public int requiredLevel() { return requiredLevel; }
    public int cooldownTicks() { return cooldownTicks; }
    public boolean unlocked(int mastery) { return mastery >= requiredLevel; }
    public static DanmakuForm safe(int id) {
        return id < 0 || id >= values().length ? null : values()[id];
    }
}
