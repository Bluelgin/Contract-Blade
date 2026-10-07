import com.maidweapon.forge.system.fox.ShrineStoryPolicy;
import com.maidweapon.forge.system.fox.ShrineStoryPolicy.Context;
import com.maidweapon.forge.system.fox.ShrineStoryPolicy.Progress;
import static com.maidweapon.forge.system.fox.ShrineStoryPolicy.Beat.*;

public final class ShrineStoryPolicyTest {
    private static int cases;

    public static void main(String[] args) {
        for (int bits = 0; bits < 64; bits++) for (int flags = 0; flags < 16; flags++) {
            var p = new Progress((bits & 1) != 0, (bits & 2) != 0, (bits & 4) != 0,
                    (bits & 8) != 0, (bits & 16) != 0, (bits & 32) != 0);
            var context = new Context(false, (flags & 1) != 0, (flags & 2) != 0,
                    (flags & 4) != 0, (flags & 8) != 0);
            var expected = !p.greeted() && !p.encountered() && !p.memoryHeard() && !p.reunited()
                    ? GREETING : context.followingDay() && !p.memoryHeard() ? MEMORY : NONE;
            check(ShrineStoryPolicy.next(p, context) == expected,
                    "missing provider never opens reunion, Divine plot or echo");
        }
        var fresh = new Progress(false, false, false, false, false, false);
        var greeted = new Progress(true, false, false, false, false, false);
        var encountered = new Progress(true, true, false, false, false, false);
        var reunited = new Progress(true, true, true, false, false, false);
        var arrived = new Progress(true, true, true, false, false, true);
        var completed = new Progress(true, true, true, true, false, true);
        check(ShrineStoryPolicy.next(fresh, tetra(false, false, false, false)) == GREETING,
                "acquisition day only greets without Akatsuki");
        check(ShrineStoryPolicy.next(greeted, tetra(false, false, false, false)) == NONE,
                "same-day repeated ticks cannot start request");
        check(ShrineStoryPolicy.next(greeted, tetra(false, true, false, false)) == ENCOUNTER,
                "following day opens request");
        check(ShrineStoryPolicy.next(fresh, tetra(true, false, false, false)) == REUNION,
                "carrying Akatsuki bypasses next-day delay");
        check(ShrineStoryPolicy.next(greeted, tetra(true, false, false, false)) == REUNION,
                "Akatsuki brought later on acquisition day also bypasses delay");
        check(ShrineStoryPolicy.next(encountered, tetra(true, false, false, false)) == REUNION,
                "late Akatsuki after request receives reunion");
        check(ShrineStoryPolicy.next(reunited, tetra(true, true, false, false)) == NONE,
                "completed reunion never repeats");
        check(ShrineStoryPolicy.next(new Progress(false, true, false, false, false, false),
                tetra(false, false, false, false)) == NONE, "legacy encounter does not replay greeting");
        check(ShrineStoryPolicy.next(greeted, tetra(false, false, true, true)) == NONE,
                "entering or clearing Divine does not skip delayed request");
        check(ShrineStoryPolicy.next(greeted, tetra(false, true, true, true)) == ENCOUNTER,
                "delayed request precedes arrival even for a veteran");
        check(ShrineStoryPolicy.next(reunited, tetra(true, false, true, false)) == ARRIVAL,
                "actual arrival recognizes atmosphere without a false clear");
        check(ShrineStoryPolicy.next(arrived, tetra(true, false, true, false)) == NONE,
                "active replay cannot open echo");
        check(ShrineStoryPolicy.next(arrived, tetra(false, false, true, true)) == ECHO,
                "actual cleared domain opens echo after arrival");
        check(ShrineStoryPolicy.next(completed, tetra(true, true, true, true)) == NONE,
                "completed domain beats never replay");
        check(ShrineStoryPolicy.next(new Progress(true, true, false, true, false, true),
                tetra(true, false, true, true)) == REUNION, "late reunion preserves old echo");
        check(ShrineStoryPolicy.next(new Progress(true, false, false, false, true, false),
                tetra(false, true, false, false)) == ENCOUNTER, "late provider install has its own request");
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

    private static Context tetra(boolean akatsuki, boolean followingDay, boolean inDivine, boolean cleared) {
        return new Context(true, akatsuki, followingDay, inDivine, cleared);
    }

    private static void check(boolean result, String message) {
        cases++;
        if (!result) throw new AssertionError(message);
    }
}
