package com.maidweapon.forge.system.fox.challenge;

/** Shared landing/engagement separation. No continuous movement policy. */
public final class BlackFoxSpacing {
    public static final double MIN = 4, MAX = 5, TARGET = 4.5;
    public static final double CLOSE = 3, FAR = 7;
    public static final int PARRY_HOLD = 12;
    public static boolean needsAdjustment(double distance) { return distance<CLOSE || distance>FAR; }
    private BlackFoxSpacing() { }
}
