package com.maidweapon.forge.system.fox;

/** Formation lifetime and switching window; attack timing belongs to native SlashBlade states. */
public final class BlackFoxSwordCoreTimeline {
    public static final int DURATION = 400, DEPLOY = 12, RADIUS = 16, MAX_TARGETS = 8, COOLDOWN = 0;
    public static boolean canFollow(int age, boolean following) { return age >= DEPLOY && age < DURATION - 8; }
    private BlackFoxSwordCoreTimeline() { }
}
