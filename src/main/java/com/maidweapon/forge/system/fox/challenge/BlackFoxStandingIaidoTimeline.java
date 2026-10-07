package com.maidweapon.forge.system.fox.challenge;

/** Long, visible charge; a four-tick committed aim window before a single strike. */
public final class BlackFoxStandingIaidoTimeline {
    public static final int LOCK=46, DRAW=48, HIT=50, END=70, COOLDOWN=160;
    public static boolean pose(BlackFoxFight.Skill skill) {
        return skill==BlackFoxFight.Skill.DIMENSION_STRIKE || skill==BlackFoxFight.Skill.STANDING_IAIDO;
    }
    public static float poseAge(float age) {
        if(age<DRAW) return BlackFoxIaidoTimeline.CHARGE+Math.max(0,age)/DRAW*12;
        return BlackFoxIaidoTimeline.DRAW+age-DRAW;
    }
    public static float animationTick(float age) { return BlackFoxIaidoTimeline.animationTick(poseAge(age)); }
    public static float swordProgress(float age, int slot) {
        float start=12+slot*1.6f;
        return Math.max(0,Math.min(1,(age-start)/12));
    }
    public static float swordRadius(float age, int slot) {
        float t=swordProgress(age,slot);
        return 2.6f*(1-t*t);
    }
    public static float streamProgress(float age,int slot) {
        float start=slot*.55f;
        if(age<start) return 1;
        float born=start+(float)Math.floor((age-start)/12)*12;
        if(born>LOCK-12) return 1;
        return Math.max(0,Math.min(1,(age-born)/12));
    }
    private BlackFoxStandingIaidoTimeline() { }
}
