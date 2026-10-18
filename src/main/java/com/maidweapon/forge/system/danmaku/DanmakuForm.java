package com.maidweapon.forge.system.danmaku;

/** Declarative unlock/cooldown data. Costs deliberately deferred to a future balancing PR. */
public enum DanmakuForm {
    NEEDLE(5, 160), FLOWER(5, 160), GUARD(6, 240), FANTASY(10, 900);
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
