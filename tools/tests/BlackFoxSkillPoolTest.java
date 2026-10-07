import com.maidweapon.forge.system.fox.challenge.*;
import static com.maidweapon.forge.system.fox.challenge.BlackFoxFight.Skill.*;
import java.util.EnumSet;
import java.util.Random;

public class BlackFoxSkillPoolTest {
    public static void main(String[] args) {
        int checks=0;
        for(int seed=0;seed<100;seed++) {
            var pool=new BlackFoxSkillPool(); var random=new Random(seed);
            var seen=EnumSet.noneOf(BlackFoxFight.Skill.class);
            var last=APPROACH;
            for(long now=0;now<10000;now+=10) {
                var skill=pool.select(now,6,0,random.nextInt());
                if(skill==APPROACH) continue;
                if(skill==last || !pool.ready(skill,now)) throw new AssertionError("repeat or unavailable skill");
                pool.started(skill,now); last=skill; seen.add(skill); checks++;
                if(pool.ready(skill,now+BlackFoxSkillPool.cooldown(skill)-1)) throw new AssertionError("early cooldown expiry");
                if(!pool.ready(skill,now+BlackFoxSkillPool.cooldown(skill))) throw new AssertionError("late cooldown expiry");
            }
            if(!seen.containsAll(EnumSet.of(PROBE_CUT,CROSS_CUT,RETURN_BLADE,DIMENSION_STRIKE,RIFT_CROSS,ARC_BARRAGE,STANDING_IAIDO)))
                throw new AssertionError("starved skill: " + seen);
        }
        var saturated=new BlackFoxSkillPool();
        for(var skill:BlackFoxFight.Skill.values()) saturated.started(skill,0);
        if(saturated.select(1,6,0,0)!=APPROACH) throw new AssertionError("must wait when all skills cool down");
        if(new BlackFoxSkillPool().select(0,6,0,3)!=DIMENSION_STRIKE) throw new AssertionError("Boss-local state leaked");
        var rotation=new BlackFoxSkillPool(); var seen=EnumSet.noneOf(BlackFoxFight.Skill.class);
        for(int i=0;i<11;i++) {
            var skill=rotation.select(i*200L,6,0,0);
            rotation.started(skill,i*200L); seen.add(skill);
        }
        if(seen.size()!=7) throw new AssertionError("weighted round did not rotate all normal skills");
        float previous=-1;
        for(int sample=0;sample<=7000;sample++) {
            float age=sample/100f;
            float pose=BlackFoxStandingIaidoTimeline.animationTick(age);
            if(!Float.isFinite(pose) || pose<previous || pose>56) throw new AssertionError("Standing pose clock");
            previous=pose;
            for(int slot=0;slot<12;slot++) if(BlackFoxStandingIaidoTimeline.swordRadius(age,slot)<0)
                throw new AssertionError("Sword must not overshoot body");
        }
        if(BlackFoxStandingIaidoTimeline.animationTick(50)!=28 || BlackFoxStandingIaidoTimeline.HIT!=50)
            throw new AssertionError("Standing draw must align with hit");
        System.out.println("BLACK_FOX_SKILL_POOL_PASS: " + checks + " casts across 100 seeds; cooldowns, variety, no repeats, waiting and isolation");
    }
}
