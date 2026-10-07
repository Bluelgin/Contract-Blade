package com.maidweapon.forge.system.fox.challenge;

/** Shared visual profiles and clocks only; never changes the server damage window. */
public final class BlackFoxSlashPresentation {
    public static final float LIFETIME = 24;
    public static final double MELEE_REACH = 6, MELEE_DOT = .8;
    public static final float MELEE_SWEEP = 10, MELEE_END = 16;
    public static boolean melee(BlackFoxFight.Skill skill) {
        return supported(skill) && skill!=BlackFoxFight.Skill.STANDING_IAIDO && skill!=BlackFoxFight.Skill.DIMENSION_STRIKE;
    }
    public static float arrival(float along) { return MELEE_SWEEP*(float)Math.sqrt(Math.max(0,Math.min(1,along))); }
    public static float meleeAlpha(float elapsed,float along,int layer) {
        float local=elapsed-arrival(along);
        float life=layer==0?2.5f:layer==1?3.5f:layer==2?4:6;
        if(local<0 || local>=life) return 0;
        if(layer==1) return Math.min(1,(life-local)/1.5f);
        float fade=1-local/life;
        return fade*fade;
    }
    public static float meleeAngle(float along) { return (2*along-1)*(float)Math.acos(MELEE_DOT); }
    public static boolean supported(BlackFoxFight.Skill skill) { return scale(skill) > 0; }
    public static float scale(BlackFoxFight.Skill skill) {
        return switch(skill) {
            case PROBE_CUT -> .52f;
            case COUNTER_CUT -> .55f;
            case SIDESTEP_CUT -> .62f;
            case CROSS_CUT -> .60f;
            case STANDING_IAIDO -> 1;
            case DIMENSION_STRIKE -> 1.15f;
            default -> 0;
        };
    }
    public static float roll(BlackFoxFight.Skill skill,int age) {
        return switch(skill) {
            case PROBE_CUT -> 14;
            case COUNTER_CUT -> -22.75f;
            case SIDESTEP_CUT -> -19.25f;
            case CROSS_CUT -> age<30 ? 15.75f : -15.75f;
            case DIMENSION_STRIKE -> -12;
            default -> 0;
        };
    }
    public static float speed(BlackFoxFight.Skill skill) {
        return skill==BlackFoxFight.Skill.STANDING_IAIDO || skill==BlackFoxFight.Skill.DIMENSION_STRIKE ? 1 : 1.2f;
    }
    public static float visualAge(float elapsed,float speed) {
        // Keep the four-tick sweep snappy; slow only the hold and dissolution, not the initial cut.
        return elapsed<4 ? elapsed : 4+(elapsed-4)*speed;
    }
    private BlackFoxSlashPresentation() { }
}
