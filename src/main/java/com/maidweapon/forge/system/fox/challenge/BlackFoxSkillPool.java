package com.maidweapon.forge.system.fox.challenge;

import static com.maidweapon.forge.system.fox.challenge.BlackFoxFight.Skill.*;

/** Encounter-local weighted shuffle bag. Selection is pure; cooldown is committed only on skill entry. */
public final class BlackFoxSkillPool {
    private static final BlackFoxFight.Skill[] BAG = {
        PROBE_CUT, CROSS_CUT, RETURN_BLADE, DIMENSION_STRIKE, ARC_BARRAGE,
        RIFT_CROSS, DIMENSION_STRIKE, ARC_BARRAGE, RIFT_CROSS, ARC_BARRAGE,
        STANDING_IAIDO, PHANTOM_FEINT, SEAL, SKY, SIDESTEP_CUT
    };
    private final long[] readyAt = new long[BlackFoxFight.Skill.values().length];
    private long used;
    private BlackFoxFight.Skill last = APPROACH;

    public static int cooldown(BlackFoxFight.Skill skill) {
        return switch (skill) {
            case DIMENSION_STRIKE -> BlackFoxDimensionTimeline.COOLDOWN;
            case STANDING_IAIDO -> BlackFoxStandingIaidoTimeline.COOLDOWN;
            case ARC_BARRAGE -> 180;
            case RIFT_CROSS -> 130;
            case RETURN_BLADE, PHANTOM_FEINT, SKY, SIDESTEP_CUT -> 100;
            case SEAL -> 140;
            case CROSS_CUT -> 80;
            case PROBE_CUT -> 55;
            default -> 0;
        };
    }
    public boolean ready(BlackFoxFight.Skill skill, long now) { return now >= readyAt[skill.ordinal()]; }
    public void started(BlackFoxFight.Skill skill, long now) {
        if (cooldown(skill) == 0) return; // Idle, stagger, counters and phase choreography aren't bag entries.
        readyAt[skill.ordinal()] = now + cooldown(skill);
        last = skill;
        // Consume one weighted seat even for a reactive link, so it doesn't immediately repeat in the bag.
        for (int i=0;i<BAG.length;i++) if (BAG[i]==skill && (used & (1L<<i))==0) {
            used |= 1L<<i; break;
        }
    }
    public BlackFoxFight.Skill select(long now, double distance, double height, int random) {
        if (height > 2.4 && distance < 14 && last != SKY && ready(SKY,now)) return SKY;
        if (distance < BlackFoxSpacing.CLOSE && last != SIDESTEP_CUT && ready(SIDESTEP_CUT,now)) return SIDESTEP_CUT;
        long candidates = candidates(now,distance,height,false);
        if (candidates==0) {
            candidates = candidates(now,distance,height,true);
            if (candidates==0) return APPROACH; // Respect all cooldowns; never force an unavailable attack.
            used = 0; // Remaining seats are unavailable in this context; begin a fresh eligible round.
        }
        int seat = Math.floorMod(random,Long.bitCount(candidates));
        for (int i=0;i<BAG.length;i++) if ((candidates & (1L<<i))!=0 && seat--==0) return BAG[i];
        return APPROACH;
    }
    private long candidates(long now, double distance, double height, boolean fresh) {
        long result = 0;
        for (int i=0;i<BAG.length;i++) {
            var skill = BAG[i];
            if ((!fresh && (used & (1L<<i))!=0) || skill==last || !ready(skill,now)) continue;
            if((skill==STANDING_IAIDO && last==DIMENSION_STRIKE) || (skill==DIMENSION_STRIKE && last==STANDING_IAIDO)) continue;
            boolean suitable = switch (skill) {
                case DIMENSION_STRIKE -> distance<=14 && height<=2.4;
                case STANDING_IAIDO -> distance<=10 && height<=2.4;
                case PROBE_CUT, CROSS_CUT, RETURN_BLADE -> distance<=8 && height<=2.4;
                case PHANTOM_FEINT, SEAL -> distance>8;
                case SKY -> height>2.4 && distance<14;
                case SIDESTEP_CUT -> distance<BlackFoxSpacing.CLOSE;
                default -> true;
            };
            if (suitable) result |= 1L<<i;
        }
        return result;
    }
}
