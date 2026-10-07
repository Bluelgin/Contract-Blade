import com.maidweapon.forge.system.fox.challenge.*;
import java.util.*;

public final class BlackFoxDomainPoolTest {
    public static void main(String[] args) {
        for (var skill : BlackFoxFight.Skill.values()) for (int age = 0; age < 130; age++) {
            if (BlackFoxOpenings.active(skill, age) || skill == BlackFoxFight.Skill.STAGGER
                    || skill == BlackFoxFight.Skill.DOMAIN || skill == BlackFoxFight.Skill.DOOM
                    || skill == BlackFoxFight.Skill.TRANSITION || skill == BlackFoxFight.Skill.RETURN_BLADE)
                require(!BlackFoxDomainCadence.rifts(skill, age) && !BlackFoxDomainCadence.swords(skill, age), "ambient steals recovery/counter window");
        }
        require(BlackFoxDomainCadence.RIFT_INTERVAL == 32 && BlackFoxDomainCadence.SWORD_INTERVAL == 24, "increased cadence");
        require(BlackFoxDomainCadence.rifts(BlackFoxFight.Skill.SWORD_WHEEL, 20), "wheel lacks ambient rifts");
        require(BlackFoxDomainCadence.swords(BlackFoxFight.Skill.DOMAIN_SEAL, 20), "seal lacks sword volleys");
        require(!BlackFoxDomainCadence.swords(BlackFoxFight.Skill.DOMAIN_CROSS, 20), "extra swords obscure cross counter");
        int casts = 0;
        for (int seed = 0; seed < 100; seed++) {
            var pool = new BlackFoxDomainPool(); var random = new Random(seed);
            var lastAt = new EnumMap<BlackFoxFight.Skill, Long>(BlackFoxFight.Skill.class);
            var last = BlackFoxFight.Skill.DOMAIN;
            for (long now = 32; now < 20000; now++) {
                var next = pool.select(now, random.nextInt());
                if (next == BlackFoxFight.Skill.DOMAIN) continue;
                require(next != last, "immediate repeat");
                int cooldown = switch (next) {
                    case SWORD_WHEEL -> 180; case DOMAIN_CROSS -> 200;
                    case DOMAIN_RAIN -> 240; case DOMAIN_SEAL -> 220;
                    default -> throw new AssertionError("unexpected phase-two skill");
                };
                require(!lastAt.containsKey(next) || now - lastAt.get(next) >= cooldown, "cooldown");
                lastAt.put(next, now); last = next; casts++;
                var fight = new BlackFoxFight(); fight.updatePhase(1, 400); fight.start(next);
                for (int i = 0; i < 50; i++) fight.hit(100);
                require(fight.skill() == next, "ordinary hits cancel phase-two choreography");
                now += fight.duration() + BlackFoxDomainPool.REST;
            }
            require(lastAt.size() == 4, "missing skill variety");
            pool.reset(); require(pool.select(0, 0) == BlackFoxFight.Skill.SWORD_WHEEL, "session reset");
        }
        System.out.println("BLACK_FOX_DOMAIN_POOL_PASS: " + casts + " casts, cooldowns, variety, no repeats, recovery and hit stability");
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
