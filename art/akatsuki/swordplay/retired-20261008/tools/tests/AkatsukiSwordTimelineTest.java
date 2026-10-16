import com.maidweapon.common.AkatsukiSwordTimeline;

public class AkatsukiSwordTimelineTest {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    public static void main(String[] args) {
        var a = new AkatsukiSwordTimeline();
        a.update(0, true, false, false, 0);
        check(a.motion() == AkatsukiSwordTimeline.Motion.IDLE, "idle stance stays untouched");
        a.update(1, true, true, false, 0);
        check(a.motion() == AkatsukiSwordTimeline.Motion.DRAW, "combat draws before attack");
        a.update(9, true, true, false, 0);
        check(a.motion() == AkatsukiSwordTimeline.Motion.READY, "ready after draw");
        a.update(10, true, true, true, 0);
        check(a.motion() == AkatsukiSwordTimeline.Motion.CUT_1, "first native swing");
        for (int tick = 11; tick <= 15; tick++) a.update(tick, true, true, true, tick - 10);
        check(a.motion() == AkatsukiSwordTimeline.Motion.CUT_1, "one swing is not a multi-hit combo");
        a.update(16, true, true, true, 0);
        check(a.motion() == AkatsukiSwordTimeline.Motion.CUT_2, "reset swing counter starts second cut");
        a.update(17, true, true, false, 0);
        a.update(18, true, true, true, 0);
        check(a.motion() == AkatsukiSwordTimeline.Motion.CUT_3, "third swing advances third cut");
        a.update(19, true, false, false, 0);
        a.update(26, true, false, false, 0);
        check(a.motion() == AkatsukiSwordTimeline.Motion.READY, "end attack stays drawn");
        a.update(58, true, false, false, 0);
        check(a.motion() == AkatsukiSwordTimeline.Motion.SHEATHE, "wait before sheathing");
        a.update(72, true, false, false, 0);
        check(a.motion() == AkatsukiSwordTimeline.Motion.IDLE, "idle restored");
        a.update(73, true, false, true, 0);
        check(a.motion() == AkatsukiSwordTimeline.Motion.CUT_1, "surprise hit not delayed by draw");
        a.update(74, false, true, true, 1);
        check(a.motion() == AkatsukiSwordTimeline.Motion.IDLE, "food/work/weapon swap cancel immediately");
        var b = new AkatsukiSwordTimeline();
        b.update(74, true, false, false, 0);
        check(b.motion() == AkatsukiSwordTimeline.Motion.IDLE, "independent companion clock");
        a.update(80, true, true, true, 0);
        a.update(0, true, false, false, 0);
        check(a.motion() == AkatsukiSwordTimeline.Motion.IDLE, "world clock rewind resets");
        System.out.println("AKATSUKI_TIMELINE_PASS");
    }
}
