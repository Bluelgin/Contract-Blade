package com.maidweapon.forge.system.fox;

/** Pure progression policy, independent of optional classes, models and world state. */
public final class ShrineStoryPolicy {
    public enum Beat { NONE, GREETING, ENCOUNTER, ECHO }

    public static Beat next(boolean bladeTetra, boolean greeted, boolean encountered,
                            boolean echoHeard, boolean divineCleared) {
        if (!bladeTetra) return greeted ? Beat.NONE : Beat.GREETING;
        if (!encountered) return Beat.ENCOUNTER;
        return divineCleared && !echoHeard ? Beat.ECHO : Beat.NONE;
    }

    private ShrineStoryPolicy() { }
}
