import com.maidweapon.forge.system.fox.challenge.BlackFoxIaidoTimeline;
import com.maidweapon.forge.system.fox.challenge.BlackFoxDimensionTimeline;

public class BlackFoxIaidoTimelineTest {
    public static void main(String[] args) {
        int checks = 0;
        float previous = -1;
        for(int sample = 0; sample <= 10000; sample++) {
            float age = sample / 100f;
            float animation = BlackFoxIaidoTimeline.animationTick(age);
            require(Float.isFinite(animation) && animation >= previous && animation <= 56, "monotonic animation clock");
            previous = animation;
            boolean charging = age >= BlackFoxIaidoTimeline.CHARGE && age < BlackFoxIaidoTimeline.DRAW;
            require(BlackFoxIaidoTimeline.charging(age) == charging, "charge window");
            if (!charging) require(BlackFoxIaidoTimeline.coreAlpha(age) == 0
                    && BlackFoxIaidoTimeline.outerAlpha(age) == 0, "no lingering charge");
            checks += 3;
        }
        require(BlackFoxIaidoTimeline.HIT == BlackFoxDimensionTimeline.HIT, "unchanged hit timing");
        require(BlackFoxIaidoTimeline.animationTick(BlackFoxIaidoTimeline.HIT) == 28, "draw ends on damage tick");
        require(!BlackFoxIaidoTimeline.drawn(BlackFoxIaidoTimeline.DRAW - .01f)
                && BlackFoxIaidoTimeline.drawn(BlackFoxIaidoTimeline.DRAW), "unsheathing boundary");
        float radius = 1;
        for(int sample = 0; sample <= 1000; sample++) {
            float current = BlackFoxIaidoTimeline.radius(sample / 1000f, .75f);
            require(current >= 0 && current <= radius, "energy only moves inward");
            radius = current;
            checks++;
        }
        System.out.println("BLACK_FOX_IAIDO_TIMELINE_PASS: " + checks + " samples, charge/draw boundaries, unchanged strike time");
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
