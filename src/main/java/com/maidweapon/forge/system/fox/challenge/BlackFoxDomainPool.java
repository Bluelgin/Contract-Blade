package com.maidweapon.forge.system.fox.challenge;

/** Phase-two selection only: independent cooldowns, no repeats and an explicit breathing gap. */
public final class BlackFoxDomainPool {
    public static final int REST = 32;
    private static final BlackFoxFight.Skill[] SKILLS = {BlackFoxFight.Skill.SWORD_WHEEL,
            BlackFoxFight.Skill.DOMAIN_CROSS, BlackFoxFight.Skill.DOMAIN_RAIN, BlackFoxFight.Skill.DOMAIN_SEAL};
    private static final int[] COOLDOWNS = {180, 200, 240, 220};
    private final long[] ready = new long[4];
    private int last = -1;
    public BlackFoxFight.Skill select(long now, int random) {
        int start = Math.floorMod(random, SKILLS.length);
        for (int i = 0; i < SKILLS.length; i++) {
            int index = (start + i) % SKILLS.length;
            if (index != last && now >= ready[index]) {
                last = index; ready[index] = now + COOLDOWNS[index]; return SKILLS[index];
            }
        }
        return BlackFoxFight.Skill.DOMAIN;
    }
    public void reset() { java.util.Arrays.fill(ready, 0); last = -1; }
}
