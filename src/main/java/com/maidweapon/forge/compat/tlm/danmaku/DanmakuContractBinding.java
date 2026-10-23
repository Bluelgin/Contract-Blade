package com.maidweapon.forge.compat.tlm.danmaku;

/** Prevent in-flight bullets from inheriting a different ordinary maid's authorization. */
public final class DanmakuContractBinding {
    public static boolean accepts(String origin, String current) {
        return origin != null && !origin.isBlank() && origin.equals(current);
    }
    private DanmakuContractBinding() { }
}
