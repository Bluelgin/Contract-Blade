package com.maidweapon.forge.system.fox.challenge;

/** Pure encounter timing; movement and individual skills are implemented separately. */
public final class BlackFoxFight {
    public enum Skill { APPROACH, RETURN_BLADE, SKY, SEAL, STAGGER, DEFEATED, UNSEAL, TRANSITION, DOMAIN, DOOM, SIDESTEP_CUT, CROSS_CUT, PHANTOM_FEINT, COUNTER_CUT, PROBE_CUT, DIMENSION_STRIKE, RIFT_CROSS, ARC_BARRAGE, STANDING_IAIDO, SWORD_WHEEL, DOMAIN_CROSS, DOMAIN_RAIN, DOMAIN_SEAL, TRIPLE_COMBO, SEAL_ARRAY }
    public enum Motion { READY, WALK, STEP, DASH, RETURN, CROSS, GUARD, FINISH, AIR, LAND, SEAL, STAGGER, DEFEATED, RUSH }
    private Skill skill = Skill.APPROACH;
    private int age;
    private boolean phaseTwo;
    private float tempoScale = .8f;
    private boolean intensified;
    private int comboAge = -1;
    private int comboRound;
    public int parryRound() {
        return switch (skill) {
            case TRIPLE_COMBO -> comboRound;
            case DIMENSION_STRIKE -> Math.min(2, Math.max(0, (age - 1) / BlackFoxDimensionTimeline.ROUND));
            case RIFT_CROSS -> age >= 40 ? 1 : 0;
            case DOMAIN_CROSS -> age >= BlackFoxDomainCross.SECOND ? 1 : 0;
            case UNSEAL -> Math.min(2, Math.max(0, (age - 6) / 18));
            default -> 0;
        };
    }
    void comboRound(int value) { comboRound = value; }
    public boolean nativeComboSkill() { return skill == Skill.RETURN_BLADE || skill == Skill.TRIPLE_COMBO; }
    public int nativeComboAge() { return skill == Skill.TRIPLE_COMBO ? comboAge : age; }
    void comboAge(int value) { comboAge = value; }
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
        skill = next; age = 0; comboAge = -1; comboRound = 0; sequence++;
        skillPool.started(next,clock,tempoScale);
        if (next == Skill.UNSEAL) pressure = Math.max(0, pressure - PRESSURE_COST);
    }
    public void advance() {
        age++;
        clock++;
        if (age % 20 == 0 && skill != Skill.STAGGER) posture = Math.max(0, posture - 2);
    }
    public boolean updatePhase(float health, float maxHealth) {
        float ratio = maxHealth > 0 ? health / maxHealth : 1;
        intensified = ratio <= .5f;
        tempoScale = ratio <= .3f ? .6f : intensified ? .68f : .8f;
        return false;
    }
    /** Only the final core uses the old phase flag; low health no longer enters the domain. */
    public void beginFinalStand() { phaseTwo = true; }
    public Skill choose(double distance, double height, int choice) {
        return pressure >= PRESSURE_COST ? Skill.UNSEAL : skillPool.select(clock,distance,height,choice,intensified);
    }
    public boolean ready(Skill candidate) { return skillPool.ready(candidate,clock); }
    public int rushTimeout() { return 160; } // Safety ceiling only; native B1-B7 owns natural completion.
    void finishTripleCombo() { if (skill == Skill.TRIPLE_COMBO) age = BlackFoxTripleCombo.DURATION; }
    /** Natural native-stage completion ends the counter window, then leaves eight ticks to recover. */
    public void endRush() { if (skill == Skill.RETURN_BLADE && age < rushTimeout()) age = rushTimeout(); }
    public boolean guard() { return clashWindow() && nativeComboAge() < rushTimeout() - 20 && nativeComboAge() % 6 >= 4; }
    public boolean clashWindow() { return nativeComboSkill() && nativeComboAge() >= 8 && nativeComboAge() < rushTimeout(); }
    public int duration() {
        return switch (skill) {
            case APPROACH -> Math.max(1, Math.round(8 * tempoScale));
            case RETURN_BLADE -> rushTimeout() + 8; case SKY -> 38; case SEAL -> 46; case UNSEAL -> 72;
            case TRIPLE_COMBO -> BlackFoxTripleCombo.DURATION;
            case SEAL_ARRAY -> 24;
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
        boolean failed = nativeComboSkill();
        if (failed) pressure = Math.min(PRESSURE_COST, pressure + 1);
        start(Skill.APPROACH);
        return failed;
    }
    public void counter() { pressure = Math.max(0, pressure - 1); }
    public void clash() { posture = 0; start(Skill.STAGGER); }
    public void loseBalance() { clash(); staggerTicks=BlackFoxBalance.RECOVERY_TICKS; }
    public boolean offBalance() { return skill==Skill.STAGGER && staggerTicks==BlackFoxBalance.RECOVERY_TICKS; }
    public void wakeFaster() { if(offBalance()) age=Math.min(staggerTicks-1,age+BlackFoxBalance.HIT_RECOVERY); }
    public void hit(float damage) {
        // Health damage still lands. Only five confirmed parries may interrupt normal combat.
    }
    public Motion motion() {
        return switch (skill) {
            case APPROACH -> Motion.READY;
            case SEAL_ARRAY -> Motion.GUARD;
            // Registered native B state, not this pure encounter policy, owns attack pose/timing.
            case RETURN_BLADE, TRIPLE_COMBO -> Motion.GUARD;
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
