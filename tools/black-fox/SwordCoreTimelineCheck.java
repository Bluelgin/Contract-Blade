import com.maidweapon.forge.system.fox.BlackFoxSwordCoreTimeline;

public final class SwordCoreTimelineCheck {
    private static void check(boolean result, String message) { if (!result) throw new AssertionError(message); }
    public static void main(String[] args) {
        check(BlackFoxSwordCoreTimeline.DURATION == 400, "Twenty-second fixed formation");
        check(BlackFoxSwordCoreTimeline.COOLDOWN == 0, "No artificial SA cooldown");
        check(!BlackFoxSwordCoreTimeline.canFollow(11, false), "No double-click during deployment");
        check(BlackFoxSwordCoreTimeline.canFollow(12, false), "Deployment complete enables chain");
        check(BlackFoxSwordCoreTimeline.canFollow(20, true), "Allows returning to barrage mode");
        check(!BlackFoxSwordCoreTimeline.canFollow(392, false), "Closing formation cannot be extended");
        boolean following = false;
        for (int age = 20; age <= 100; age += 20) {
            check(BlackFoxSwordCoreTimeline.canFollow(age, following), "Repeated switches accepted in both modes");
            following = !following;
        }
        check(BlackFoxSwordCoreTimeline.MAX_TARGETS == 8 && BlackFoxSwordCoreTimeline.RADIUS == 16, "Expanded but bounded targets and radius");
        System.out.println("SWORD_CORE_PASS: fixed lifetime, bidirectional switches, deployment/closing guards and target limits");
    }
}
