package com.maidweapon.forge.system.fox.challenge;

/** Ambient phase-two pressure; never steals an advertised recovery/counter window. */
public final class BlackFoxDomainCadence {
    public static final int RIFT_INTERVAL = 32, SWORD_INTERVAL = 24;
    public static boolean rifts(BlackFoxFight.Skill skill, int age) {
        if (BlackFoxOpenings.active(skill, age)) return false;
        return switch (skill) {
            case SWORD_WHEEL, DOMAIN_CROSS, DOMAIN_RAIN, DOMAIN_SEAL -> true;
            default -> false;
        };
    }
    public static boolean swords(BlackFoxFight.Skill skill, int age) {
        return rifts(skill, age) && (skill == BlackFoxFight.Skill.DOMAIN_RAIN || skill == BlackFoxFight.Skill.DOMAIN_SEAL);
    }
    private BlackFoxDomainCadence() { }
}
