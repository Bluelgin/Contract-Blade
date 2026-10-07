package com.maidweapon.forge.compat;

/** Boss-only presentation palette. Never changes native attack rank or damage. */
public final class BlackFoxSlashColors {
    public static int blade(boolean phaseTwo) { return phaseTwo ? 0xB04CE5 : 0xAC83D9; }
    public static int rim(boolean phaseTwo) { return phaseTwo ? 0x582672 : 0x56416C; }
    private BlackFoxSlashColors() { }
}
