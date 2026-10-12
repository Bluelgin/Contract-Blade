package com.maidweapon.forge.system.fox;

/** Priority policy shared by barrage and hunters; callers filter range/visibility before ranking. */
public final class BlackFoxSwordCoreTargeting {
    public static int priority(BlackFoxSwordCoreGrowth.Profile growth, boolean retaliating, boolean focused,
                               boolean threatensOwner, boolean marked) {
        if (growth.has(BlackFoxSwordCoreGrowth.RETALIATE) && retaliating) return 4;
        if (growth.has(BlackFoxSwordCoreGrowth.FOCUS) && focused) return 3;
        if (growth.has(BlackFoxSwordCoreGrowth.PROTECT) && threatensOwner) return 2;
        return growth.has(BlackFoxSwordCoreGrowth.MARK) && marked ? 1 : 0;
    }
    private BlackFoxSwordCoreTargeting() { }
}
