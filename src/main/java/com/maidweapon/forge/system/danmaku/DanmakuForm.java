package com.maidweapon.forge.system.danmaku;

/** Four distinct roles; cooldowns deliberately separate from projectile count. */
public enum DanmakuForm {
    NEEDLE(5, 120), FLOWER(5, 180), GUARD(5, 320), FANTASY(10, 800);
    private final int requiredLevel;
    private final int cooldownTicks;
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
