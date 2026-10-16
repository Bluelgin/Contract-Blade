import com.maidweapon.forge.system.fox.ShrineStoryPolicy;
import com.maidweapon.forge.system.fox.ShrineStoryPolicy.Context;
import com.maidweapon.forge.system.fox.ShrineStoryPolicy.Progress;
import static com.maidweapon.forge.system.fox.ShrineStoryPolicy.Beat.*;

public final class ShrineStoryPolicyTest {
    private static int cases;

    public static void main(String[] args) {
        // Include legacy reunion/echo saves and every optional-provider state.
        for (int bits = 0; bits < 64; bits++) for (int flags = 0; flags < 32; flags++) {
            var p = new Progress((bits & 1) != 0, (bits & 2) != 0, (bits & 4) != 0,
                    (bits & 8) != 0, (bits & 16) != 0, (bits & 32) != 0);
            var context = new Context((flags & 1) != 0, (flags & 2) != 0, (flags & 4) != 0,
                    (flags & 8) != 0, (flags & 16) != 0);
            check(ShrineStoryPolicy.next(p, context) == NONE,
                    "background dialogue stays dormant for every provider and legacy progress state");
        }
        check(ShrineStoryPolicy.gameDay(0) == 0 && ShrineStoryPolicy.gameDay(23999) == 0,
                "day includes its night");
        check(ShrineStoryPolicy.gameDay(24000) == 1 && ShrineStoryPolicy.gameDay(48000) == 2,
                "dawn and sleep skips advance date");
        check(!ShrineStoryPolicy.followingDay(5, 5), "same date never qualifies");
        check(ShrineStoryPolicy.followingDay(5, 6), "next date qualifies without twenty minutes online");
        check(ShrineStoryPolicy.followingDay(5, 10), "offline missed date is not lost");
        check(!ShrineStoryPolicy.followingDay(5, 4), "time moved backward cannot cause early request");
        System.out.println("SHRINE_STORY_POLICY_PASS: " + cases + " cases");
    }

    private static void check(boolean result, String message) {
        cases++;
        if (!result) throw new AssertionError(message);
    }
}
