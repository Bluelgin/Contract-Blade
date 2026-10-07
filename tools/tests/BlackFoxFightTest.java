import com.maidweapon.forge.system.fox.challenge.BlackFoxFight;
import com.maidweapon.forge.system.fox.challenge.BlackFoxSpacing;
import com.maidweapon.forge.system.fox.challenge.BlackFoxSlashPresentation;
import com.maidweapon.forge.system.fox.challenge.BlackFoxTactics;
import static com.maidweapon.forge.system.fox.challenge.BlackFoxFight.Skill.*;

public final class BlackFoxFightTest {
    private static int cases;
    public static void main(String[] args) {
        var balance=new com.maidweapon.forge.system.fox.challenge.BlackFoxBalance();
        for(int i=0;i<4;i++) { balance.parried(i*5); check(balance.progress()==i+1,"HUD follows confirmed parries"); check(!balance.take(),"five parries required"); }
        balance.parried(20); balance.parried(20); check(balance.progress()==5,"pending fifth parry remains visible");
        check(balance.take() && !balance.take(),"one fifth-parry reward");
        check(balance.progress()==0,"parry marks reset after balance consumed");
        var down=new BlackFoxFight(); down.loseBalance();
        check(down.duration()==100 && down.offBalance(),"five second balance recovery");
        down.wakeFaster(); check(down.age()==10,"accepted hit accelerates waking by half a second");
        var dimension = new BlackFoxFight();
        int skills=0;
        for (int choice=0;choice<10;choice++) {
            var selected=new BlackFoxFight().choose(6,0,choice);
            if (selected==DIMENSION_STRIKE || selected==ARC_BARRAGE || selected==RIFT_CROSS) skills++;
        }
        check(skills==7,"seven normal-distance skill slots versus three melee slots");
        var repeat = new BlackFoxFight(); repeat.start(ARC_BARRAGE); repeat.start(APPROACH);
        for(int choice=0;choice<100;choice++) check(repeat.choose(6,0,choice)!=ARC_BARRAGE,"no repeated/cooling skill");
        check(com.maidweapon.forge.system.fox.challenge.BlackFoxOpenings.active(RIFT_CROSS,8)
                && !com.maidweapon.forge.system.fox.challenge.BlackFoxOpenings.active(RIFT_CROSS,24)
                && com.maidweapon.forge.system.fox.challenge.BlackFoxOpenings.active(RIFT_CROSS,44),"cross preparation and recovery, not arbitrary permanent exposure");
        check(com.maidweapon.forge.system.fox.challenge.BlackFoxOpenings.active(ARC_BARRAGE,12)
                && !com.maidweapon.forge.system.fox.challenge.BlackFoxOpenings.active(STAGGER,12),"normal hits can discover cast opening but never restart stagger");
        check(dimension.choose(6,0,3)==DIMENSION_STRIKE,"dimension strike enters normal selection");
        check(dimension.ready(DIMENSION_STRIKE),"selection alone does not consume cooldown");
        dimension.start(DIMENSION_STRIKE); dimension.start(APPROACH);
        check(dimension.choose(6,0,3)!=DIMENSION_STRIKE,"dimension strike cooldown prevents spam");
        for(int i=0;i<com.maidweapon.forge.system.fox.challenge.BlackFoxDimensionTimeline.COOLDOWN;i++)dimension.advance();
        check(dimension.ready(DIMENSION_STRIKE),"dimension strike cooldown expires");
        check(dimension.choose(6,0,3)!=DIMENSION_STRIKE,"no consecutive repeat even after cooldown");
        check(com.maidweapon.forge.system.fox.challenge.BlackFoxDimensionTimeline.bodyAlpha(20)==0
                && com.maidweapon.forge.system.fox.challenge.BlackFoxDimensionTimeline.bodyAlpha(49)==0
                && com.maidweapon.forge.system.fox.challenge.BlackFoxDimensionTimeline.bodyAlpha(52)==1,"long concealment and sudden emergence");
        check(com.maidweapon.forge.system.fox.challenge.BlackFoxDimensionTimeline.slashAlpha(63)==0
                && com.maidweapon.forge.system.fox.challenge.BlackFoxDimensionTimeline.slashAlpha(64)==1
                && com.maidweapon.forge.system.fox.challenge.BlackFoxDimensionTimeline.slashAlpha(78)==0,"slash bursts on hit then quickly dissipates");
        for (var skill : BlackFoxFight.Skill.values()) {
            var fight = new BlackFoxFight(); fight.start(skill);
            for (int tick = 0; tick <= 110; tick++) {
                int age = fight.age();
                check(fight.guard() == (skill == RETURN_BLADE && age >= 8 && age < 20 && age % 6 >= 4), "brief guards between stationary cuts");
                check(fight.clashWindow() == (skill == RETURN_BLADE && age >= 8 && age < 40), "Combo B counter works before the first-phase deadline");
                check(!fight.guard() || fight.clashWindow(), "a true Combo B counter takes priority over guarding");
                check(fight.finished() == (age >= fight.duration()), "bounded skill timeline");
                fight.advance();
            }
        }
        var fight = new BlackFoxFight();
        check(!fight.updatePhase(201, 400) && fight.updatePhase(200, 400), "half-health phase");
        check(!fight.updatePhase(100, 400), "phase transition once");
        fight.start(CROSS_CUT); check(fight.duration() == 60, "two timed cuts leave a bounded recovery");
        fight.start(RETURN_BLADE);
        for (int i = 0; i < 100; i++) fight.hit(100);
        check(fight.skill() == RETURN_BLADE && fight.posture() == 0, "ordinary hits cannot interrupt Combo B");
        check(fight.rushTimeout() == 100, "second phase has a five-second counter deadline");
        fight.start(APPROACH); check(fight.duration() == 8, "shorter time between attacks");
        fight.start(RETURN_BLADE); for (int i = 0; i < 26; i++) fight.advance();
        fight.clash(); check(fight.skill() == STAGGER && !fight.clashWindow() && fight.age() == 0, "clash cancels pending finisher");
        for (int i = 0; i < 20; i++) fight.hit(100);
        check(fight.age() == 0 && fight.skill() == STAGGER, "hits do not restart opening indefinitely");
        fight.start(APPROACH); for (int i = 0; i < 25; i++) fight.hit(1);
        check(fight.skill() == STAGGER, "ordinary damage can break posture without SlashBlade");
        fight.start(STANDING_IAIDO); for(int i=0;i<100;i++) fight.hit(100);
        check(fight.skill()==STANDING_IAIDO,"visible charge cannot be canceled by ordinary hits");
        fight.start(APPROACH);
        var far = new BlackFoxFight();
        for(int choice=0;choice<100;choice++) check(far.choose(12,0,choice)!=PROBE_CUT
                && far.choose(12,0,choice)!=CROSS_CUT && far.choose(12,0,choice)!=RETURN_BLADE,"distant players do not draw close melee");
        check(fight.choose(5, 5, 0) == SKY, "bounded air pursuit");
        check(fight.choose(5, 0, 0) == PROBE_CUT, "midrange prefers a probe rather than random teleport attacks");
        check(fight.choose(2,0,0)==SIDESTEP_CUT,"crowded relocation");
        fight.start(SIDESTEP_CUT); fight.start(APPROACH);
        check(fight.choose(2,0,0)!=SIDESTEP_CUT,"crowded relocation cooldown");
        check(BlackFoxSpacing.MIN < BlackFoxSpacing.TARGET && BlackFoxSpacing.MAX > BlackFoxSpacing.TARGET,
                "blink landing retains readable separation");
        check(BlackFoxSpacing.needsAdjustment(2.99) && BlackFoxSpacing.needsAdjustment(7.01),"adjust crowding and escape");
        check(!BlackFoxSpacing.needsAdjustment(3) && !BlackFoxSpacing.needsAdjustment(7)
                && !BlackFoxSpacing.needsAdjustment(4.5),"distance hysteresis avoids constant corrections");
        check(BlackFoxSpacing.MAX < BlackFoxSlashPresentation.MELEE_REACH,"landing inside melee reach");
        var shortB = new BlackFoxFight(); shortB.start(RETURN_BLADE);
        for (int i = 0; i < 22; i++) shortB.advance();
        shortB.endRush();
        check(!shortB.clashWindow() && !shortB.finished(), "native segment boundary closes counter window but leaves recovery");
        for (int i = 0; i < 8; i++) shortB.advance();
        check(shortB.complete() && shortB.pressure() == 1, "short B awards exactly one unanswered sequence after recovery");
        var tactics = new BlackFoxTactics();
        tactics.pressured(1); tactics.pressured(1); tactics.pressured(1);
        check(!tactics.reposition(1), "same-tick hits cannot multiply tactical pressure");
        tactics.pressured(2); tactics.pressured(3);
        check(tactics.reposition(3) && !tactics.reposition(3), "three hits schedule one reposition, not a new permanent meter");
        var swordplay = new com.maidweapon.forge.system.fox.challenge.BlackFoxSwordplay();
        check(!swordplay.parry(1, 1, 0) && swordplay.momentum() == 2, "first real exchange weakens momentum");
        check(!swordplay.parry(2, 1, 0) && swordplay.momentum() == 2, "same attack window never weakens momentum twice");
        check(!swordplay.parry(31, 1, 1) && swordplay.momentum() == 1, "separate second cut has its own exchange window");
        check(swordplay.parry(50, 2, 0) && swordplay.momentum() == 3, "third distinct exchange breaks and resets momentum");
        swordplay.parry(60, 3, 0); swordplay.advance(139, false);
        check(swordplay.momentum() == 2, "disengagement has an eighty-tick grace period");
        swordplay.advance(140, true); check(swordplay.momentum() == 2, "remaining in the duel never restores momentum");
        swordplay.advance(140, false); check(swordplay.momentum() == 3, "distance restores one momentum step slowly");
        fight.start(DEFEATED); fight.hit(1000);
        check(fight.skill() == DEFEATED && !fight.guard() && !fight.clashWindow(), "defeat cannot be reopened by attacks");
        var pressure = new BlackFoxFight();
        for (int attempt = 1; attempt <= 3; attempt++) {
            pressure.start(RETURN_BLADE);
            while (!pressure.finished()) {
                check(!pressure.complete(), "pressure never increases before recovery completes");
                check(pressure.pressure() == attempt - 1, "ordinary cuts do not add pressure per tick");
                pressure.advance();
            }
            check(pressure.complete() && pressure.pressure() == attempt, "one failed pursuit adds exactly one pressure");
            check(!pressure.complete() && pressure.pressure() == attempt, "failure cannot be accounted twice");
        }
        check(pressure.choose(25, 8, 0) == UNSEAL && pressure.pressure() == 3, "special takes priority, selecting does not spend pressure");
        pressure.start(UNSEAL);
        check(pressure.pressure() == 0, "special spends three pressure once on entry");
        pressure.start(RETURN_BLADE); while (!pressure.finished()) pressure.advance(); pressure.complete();
        pressure.start(RETURN_BLADE); pressure.advance(); pressure.counter();
        check(pressure.pressure() == 0 && pressure.skill() == STAGGER, "successful native B counter reduces pressure");
        for (int i = 0; i < 100; i++) pressure.advance(); pressure.complete();
        check(pressure.pressure() == 0, "canceled pursuit never awards a timeout");
        pressure.start(RETURN_BLADE);
        check(pressure.motion() == BlackFoxFight.Motion.GUARD, "pure policy does not invent a replacement native B timeline");
        for (int i = 0; i < 80; i++) pressure.advance();
        check(!pressure.clashWindow(), "late B cannot evade timeout");
        System.out.println("BLACK_FOX_POLICY_PASS: " + cases + " checks");
    }
    private static void check(boolean value, String message) {
        cases++; if (!value) throw new AssertionError(message);
    }
}
