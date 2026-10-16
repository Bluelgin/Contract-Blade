import com.maidweapon.forge.system.fox.challenge.BlackFoxParryCredit;
import com.maidweapon.forge.system.fox.challenge.BlackFoxFight.Skill;

public final class ParryCreditCheck {
    private static void check(boolean result, String message) { if (!result) throw new AssertionError(message); }
    public static void main(String[] args) {
        var credit = new BlackFoxParryCredit();
        for (var skill : new Skill[]{Skill.PROBE_CUT, Skill.CROSS_CUT, Skill.SIDESTEP_CUT, Skill.COUNTER_CUT, Skill.PHANTOM_FEINT})
            check(!credit.accept(skill, 1, 0), "Ordinary cuts cannot build stance break");
        for (var skill : new Skill[]{Skill.TRIPLE_COMBO, Skill.DIMENSION_STRIKE}) {
            for (int round = 0; round < 3; round++) {
                check(credit.accept(skill, 2, round), "Each committed round counts");
                check(!credit.accept(skill, 2, round), "Round cannot inflate from multiple projectiles");
            }
            check(credit.accept(skill, 3, 0), "A new cast has a new window");
            check(!credit.accept(skill, 2, 0), "Delayed projectile preserves previous credit");
        }
        for (int slot = 0; slot < 4; slot++) {
            check(credit.accept(Skill.SEAL_ARRAY, 4, slot), "Each core can grant one credit");
            check(!credit.accept(Skill.SEAL_ARRAY, 4, slot), "Repeating core barrage does not inflate credit");
        }
        var pool = new com.maidweapon.forge.system.fox.challenge.BlackFoxSkillPool();
        var selected = new java.util.HashSet<Skill>();
        for (int seat = 0; seat < 100; seat++) selected.add(pool.select(0, 10, 0, seat, false));
        for (var skill : new Skill[]{Skill.SWORD_WHEEL, Skill.DOMAIN_CROSS, Skill.DOMAIN_RAIN, Skill.DOMAIN_SEAL}) {
            check(selected.contains(skill), "Legacy domain attack available at full health: " + skill);
            check(com.maidweapon.forge.system.fox.challenge.BlackFoxSkillPool.cooldown(skill) > 0, "Legacy cooldown is enforced");
        }
        check(!selected.contains(Skill.TRIPLE_COMBO), "Triple B remains low-health only");
        pool.started(Skill.SWORD_WHEEL, 10, .8f);
        check(!pool.ready(Skill.SWORD_WHEEL, 153) && pool.ready(Skill.SWORD_WHEEL, 154), "Wheel respects scaled cooldown");
        System.out.println("PARRY_CREDIT_PASS: skill filter, per-round deduplication, delayed windows and four-core limits");
    }
}
