import com.maidweapon.forge.system.fox.ShrineStoryPolicy;
import static com.maidweapon.forge.system.fox.ShrineStoryPolicy.Beat.*;

public final class ShrineStoryPolicyTest {
    public static void main(String[] args) {
        // All persisted-flag combinations: missing BladeTetra never opens its story.
        for (int bits = 0; bits < 16; bits++) {
            boolean greeted = (bits & 1) != 0;
            var beat = ShrineStoryPolicy.next(false, greeted, (bits & 2) != 0,
                    (bits & 4) != 0, (bits & 8) != 0);
            check(beat == (greeted ? NONE : GREETING), "standalone isolation");
        }
        check(ShrineStoryPolicy.next(true, true, false, false, false) == ENCOUNTER,
                "installing BladeTetra later opens encounter");
        check(ShrineStoryPolicy.next(true, false, false, false, true) == ENCOUNTER,
                "encounter precedes echo even for a veteran");
        check(ShrineStoryPolicy.next(true, false, true, false, false) == NONE,
                "uncleared domain does not advance");
        check(ShrineStoryPolicy.next(true, false, true, false, true) == ECHO,
                "existing cleared domain opens echo");
        check(ShrineStoryPolicy.next(true, false, true, true, true) == NONE,
                "completed beats never replay");
        System.out.println("SHRINE_STORY_POLICY_PASS: 21 cases");
    }

    private static void check(boolean result, String message) {
        if (!result) throw new AssertionError(message);
    }
}
