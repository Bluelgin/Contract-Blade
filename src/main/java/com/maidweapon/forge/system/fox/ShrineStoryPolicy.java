package com.maidweapon.forge.system.fox;

/** Pure progression policy, independent of optional classes, models and world state. */
public final class ShrineStoryPolicy {
    public enum Beat { NONE, GREETING, ENCOUNTER, REUNION, MEMORY, ARRIVAL, ECHO }
    public record Progress(boolean greeted, boolean encountered, boolean reunited, boolean echoHeard,
                           boolean memoryHeard, boolean domainRecognized) { }
    public record Context(boolean bladeTetra, boolean hasAkatsuki, boolean followingDay,
                          boolean inDivineDomain, boolean inClearedDivineDomain) { }

    public static Beat next(Progress progress, Context context) {
        if (context.bladeTetra() && context.hasAkatsuki() && !progress.reunited()) return Beat.REUNION;
        if (!progress.greeted() && !progress.encountered() && !progress.memoryHeard()
                && !progress.reunited()) return Beat.GREETING;
        if (!context.bladeTetra()) {
            return context.followingDay() && !progress.memoryHeard() ? Beat.MEMORY : Beat.NONE;
        }
        if (!progress.encountered()) return context.followingDay() ? Beat.ENCOUNTER : Beat.NONE;
        if (context.inDivineDomain() && !progress.domainRecognized()) return Beat.ARRIVAL;
        return context.inClearedDivineDomain() && !progress.echoHeard() ? Beat.ECHO : Beat.NONE;
    }

    public static long gameDay(long dayTime) {
        return Math.floorDiv(dayTime, 24000L);
    }

    public static boolean followingDay(long acquiredDay, long currentDay) {
        return currentDay > acquiredDay;
    }

    private ShrineStoryPolicy() { }
}
