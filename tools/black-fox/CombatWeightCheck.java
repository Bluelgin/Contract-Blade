import com.maidweapon.forge.system.fox.challenge.BlackFoxBalance;
import com.maidweapon.forge.system.fox.challenge.BlackFoxDimensionTimeline;
import com.maidweapon.forge.system.fox.challenge.BlackFoxIaidoTimeline;

/** Standalone checks for the pure encounter clocks; does not launch or modify a game world. */
public final class CombatWeightCheck {
    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }
    public static void main(String[] args) {
        var balance = new BlackFoxBalance();
        for (int i = 0; i < 4; i++) {
            balance.parried(i * 8);
            check(!balance.take(), "No interruption before fifth parry");
            balance.parried(i * 8 + 1);
            check(balance.progress() == i + 1, "Duplicate contacts do not count");
        }
        balance.parried(32);
        check(balance.progress() == 5 && balance.take() && !balance.take(), "Fifth parry triggers once");
        check(balance.progress() == 0 && BlackFoxBalance.RECOVERY_TICKS == 30, "Short recovery and reset");
        int hits = 0;
        for (int age = 1; age <= BlackFoxDimensionTimeline.END; age++) {
            float local = BlackFoxDimensionTimeline.localAge(age);
            if (local == BlackFoxDimensionTimeline.hitAge(age)) hits++;
            float pose = BlackFoxDimensionTimeline.poseAge(age);
            check(Float.isFinite(pose) && pose >= BlackFoxIaidoTimeline.CHARGE
                    && pose <= BlackFoxIaidoTimeline.END, "Finite pose on every frame");
        }
        check(hits == 3, "Exactly three slash windows");
        check(BlackFoxDimensionTimeline.localAge(35) == 1
                && BlackFoxDimensionTimeline.localAge(69) == 1, "Round boundaries");
        check(BlackFoxDimensionTimeline.hitAge(68) == 24
                && BlackFoxDimensionTimeline.hitAge(69) == 30, "Final slash delays six ticks");
        check(BlackFoxDimensionTimeline.poseAge(98) == BlackFoxIaidoTimeline.HIT, "Heavy cut pose matches hit");
        check(BlackFoxDimensionTimeline.submerged(72) && !BlackFoxDimensionTimeline.submerged(84), "Final dive immunity ends on emergence");
        check(!BlackFoxDimensionTimeline.submerged(112), "No immunity after skill end");
        System.out.println("COMBAT_WEIGHT_PASS: five-parry gate, deduplication, short recovery, three rounds and synchronized heavy hit");
    }
}
