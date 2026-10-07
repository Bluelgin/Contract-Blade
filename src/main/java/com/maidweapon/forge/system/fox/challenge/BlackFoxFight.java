package com.maidweapon.forge.system.fox.challenge;

/** Pure encounter timing; movement and individual skills are implemented separately. */
public final class BlackFoxFight {
    public enum Skill { APPROACH, RETURN_BLADE, SKY, SEAL, STAGGER, DEFEATED, UNSEAL, TRANSITION, DOMAIN, DOOM, SIDESTEP_CUT, CROSS_CUT, PHANTOM_FEINT, COUNTER_CUT, PROBE_CUT, DIMENSION_STRIKE, RIFT_CROSS, ARC_BARRAGE, STANDING_IAIDO, SWORD_WHEEL, DOMAIN_CROSS, DOMAIN_RAIN, DOMAIN_SEAL }
    public enum Motion { READY, WALK, STEP, DASH, RETURN, CROSS, GUARD, FINISH, AIR, LAND, SEAL, STAGGER, DEFEATED, RUSH }
    private Skill skill = Skill.APPROACH;
    private int age;
    private boolean phaseTwo;
    private float posture;
    private final BlackFoxSkillPool skillPool = new BlackFoxSkillPool();
    private long clock;
    public static final int PRESSURE_COST = 3;
    public static final int CORE_FALL_TICKS = 300;
    private int pressure;
    private int staggerTicks=42;
    private long sequence;
    public long sequence() { return sequence; }

    public Skill skill() { return skill; }
    public int age() { return age; }
    public boolean phaseTwo() { return phaseTwo; }
    public float posture() { return posture; }
    public int pressure() { return pressure; }
    public void start(Skill next) {
        staggerTicks=42;
        skill = next; age = 0; sequence++;
        skillPool.started(next,clock);
        if (next == Skill.UNSEAL) pressure = Math.max(0, pressure - PRESSURE_COST);
    }
    public void advance() {
        age++;
        clock++;
        if (age % 20 == 0 && skill != Skill.STAGGER) posture = Math.max(0, posture - 2);
    }
    public boolean updatePhase(float health, float maxHealth) {
        if (!phaseTwo && health <= maxHealth * 0.5f) { phaseTwo = true; return true; }
        return false;
    }
    public Skill choose(double distance, double height, int choice) {
        return pressure >= PRESSURE_COST ? Skill.UNSEAL : skillPool.select(clock,distance,height,choice);
    }
    public boolean ready(Skill candidate) { return skillPool.ready(candidate,clock); }
    public int rushTimeout() { return phaseTwo ? 100 : 40; }
    /** Natural native-stage completion ends the counter window, then leaves eight ticks to recover. */
    public void endRush() { if (skill == Skill.RETURN_BLADE && age < rushTimeout()) age = rushTimeout(); }
    public boolean guard() { return clashWindow() && age < rushTimeout() - 20 && age % 6 >= 4; }
    public boolean clashWindow() { return skill == Skill.RETURN_BLADE && age >= 8 && age < rushTimeout(); }
    public int duration() {
        return switch (skill) {
            case APPROACH -> 8;
            case RETURN_BLADE -> rushTimeout() + 8; case SKY -> 38; case SEAL -> 46; case UNSEAL -> 72;
            case STAGGER -> staggerTicks; case DEFEATED -> 100;
            case TRANSITION -> 60; case DOMAIN -> 600; case DOOM -> CORE_FALL_TICKS;
            case SIDESTEP_CUT -> 36; case CROSS_CUT -> 60; case PHANTOM_FEINT -> 50;
            case COUNTER_CUT -> 26;
            case PROBE_CUT -> 28;
            case DIMENSION_STRIKE -> BlackFoxDimensionTimeline.END;
            case RIFT_CROSS -> 64;
            case ARC_BARRAGE -> 94;
            case STANDING_IAIDO -> BlackFoxStandingIaidoTimeline.END;
            case SWORD_WHEEL -> 92; case DOMAIN_CROSS -> 88;
            case DOMAIN_RAIN -> 70; case DOMAIN_SEAL -> 110;
        };
    }
    public boolean finished() { return age >= duration(); }
    /** Account for one completed attack, never per tick or per blocked cut. */
    public boolean complete() {
        if (!finished() || skill == Skill.APPROACH || skill == Skill.DEFEATED) return false;
        boolean failed = skill == Skill.RETURN_BLADE;
        if (failed) pressure = Math.min(PRESSURE_COST, pressure + 1);
        start(Skill.APPROACH);
        return failed;
    }
    public void counter() { pressure = Math.max(0, pressure - 1); clash(); }
    public void clash() { posture = 0; start(Skill.STAGGER); }
    public void loseBalance() { clash(); staggerTicks=BlackFoxBalance.RECOVERY_TICKS; }
    public boolean offBalance() { return skill==Skill.STAGGER && staggerTicks==BlackFoxBalance.RECOVERY_TICKS; }
    public void wakeFaster() { if(offBalance()) age=Math.min(staggerTicks-1,age+BlackFoxBalance.HIT_RECOVERY); }
    public void hit(float damage) {
        // Damage still counts, but ordinary hits cannot cancel the chasing Combo B.
        if (skill == Skill.TRANSITION || skill == Skill.DOMAIN || skill == Skill.DOOM
                || skill == Skill.STAGGER || skill == Skill.DEFEATED || skill == Skill.RETURN_BLADE
                || skill == Skill.STANDING_IAIDO || skill == Skill.SWORD_WHEEL || skill == Skill.DOMAIN_CROSS
                || skill == Skill.DOMAIN_RAIN || skill == Skill.DOMAIN_SEAL) return;
        posture += Math.min(12, Math.max(2, damage * 0.3f));
        if (posture >= 50) clash();
    }
    public Motion motion() {
        return switch (skill) {
            case APPROACH -> Motion.READY;
            // Registered native B state, not this pure encounter policy, owns attack pose/timing.
            case RETURN_BLADE -> Motion.GUARD;
            case SKY -> age < 12 ? Motion.GUARD : age < 25 ? Motion.AIR : age < 35 ? Motion.LAND : Motion.READY;
            case SEAL -> age < 16 ? Motion.GUARD : age < 35 ? Motion.SEAL : Motion.STEP;
            case UNSEAL -> age < 9 || age >= 24 && age < 27 || age >= 42 && age < 45 ? Motion.GUARD
                    : age < 62 ? Motion.SEAL : Motion.READY;
            case STAGGER -> Motion.STAGGER; case DEFEATED -> Motion.DEFEATED;
            case TRANSITION, DOMAIN, DOOM -> Motion.READY;
            case SIDESTEP_CUT -> age <= 8 ? Motion.STEP : Motion.READY;
            case CROSS_CUT, PHANTOM_FEINT, COUNTER_CUT, PROBE_CUT -> Motion.READY;
            case DIMENSION_STRIKE, STANDING_IAIDO -> Motion.READY;
            case RIFT_CROSS, ARC_BARRAGE -> Motion.READY;
            case SWORD_WHEEL, DOMAIN_CROSS, DOMAIN_RAIN, DOMAIN_SEAL -> Motion.READY;
        };
    }
}
