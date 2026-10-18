package com.maidweapon.forge.client;

import com.maidweapon.forge.system.danmaku.DanmakuForm;

/** Shared circular input geometry. The displayed wheel and input agree on bounds/sectors. */
public final class DanmakuWheelGeometry {
    public static final int RADIUS = 86;
    private DanmakuWheelGeometry() { }

    public static DanmakuForm pick(double dx, double dy, int radius) {
        double distanceSq = dx * dx + dy * dy;
        double inner = radius * 0.34;
        double outer = radius * 1.04;
        if (distanceSq < inner * inner || distanceSq > outer * outer) return null;
        if (Math.abs(dx) > Math.abs(dy)) {
            return dx > 0 ? DanmakuForm.FLOWER : DanmakuForm.GUARD;
        }
        return dy > 0 ? DanmakuForm.FANTASY : DanmakuForm.NEEDLE;
    }
}
