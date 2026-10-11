import com.maidweapon.forge.system.fox.challenge.FoxChallengeCollapseTimeline;

public final class CollapseTimelineCheck {
    private static void check(boolean result) { if (!result) throw new AssertionError("Collapse clock"); }
    public static void main(String[] args) {
        check(FoxChallengeCollapseTimeline.seconds(200) == 10);
        check(FoxChallengeCollapseTimeline.seconds(181) == 10);
        check(FoxChallengeCollapseTimeline.seconds(180) == 9);
        check(FoxChallengeCollapseTimeline.seconds(1) == 1);
        check(FoxChallengeCollapseTimeline.seconds(0) == 0);
        check(FoxChallengeCollapseTimeline.progress(200) == 0);
        check(FoxChallengeCollapseTimeline.progress(0) == 1);
        check(FoxChallengeCollapseTimeline.particleInterval(61) == 8);
        check(FoxChallengeCollapseTimeline.particleInterval(60) == 4);
        int ticks = FoxChallengeCollapseTimeline.DURATION;
        for (int i = 0; i < 200; i++) ticks = Math.max(0, ticks - 1);
        check(ticks == 0);
        System.out.println("COLLAPSE_TIMELINE_PASS: ten-second countdown, accelerated final effects and finite return clock");
    }
}
