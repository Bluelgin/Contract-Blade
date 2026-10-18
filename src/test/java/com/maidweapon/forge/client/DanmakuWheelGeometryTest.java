package com.maidweapon.forge.client;

import com.maidweapon.forge.system.danmaku.DanmakuForm;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Geometry is independent of Minecraft rendering and deliberately matches wheel sectors. */
class DanmakuWheelGeometryTest {
    @Test void cardinalSectors() {
        int radius = DanmakuWheelGeometry.RADIUS;
        assertEquals(DanmakuForm.NEEDLE, DanmakuWheelGeometry.pick(0, -60, radius));
        assertEquals(DanmakuForm.FLOWER, DanmakuWheelGeometry.pick(60, 0, radius));
        assertEquals(DanmakuForm.GUARD, DanmakuWheelGeometry.pick(-60, 0, radius));
        assertEquals(DanmakuForm.FANTASY, DanmakuWheelGeometry.pick(0, 60, radius));
    }

    @Test void centerOrOutsideNeverSelects() {
        int radius = DanmakuWheelGeometry.RADIUS;
        assertNull(DanmakuWheelGeometry.pick(0, 0, radius));
        assertNull(DanmakuWheelGeometry.pick(200, 0, radius));
        assertNull(DanmakuWheelGeometry.pick(20, 0, radius));
    }

    @Test void diagonalBoundaryHasConsistentOwnership() {
        int radius = DanmakuWheelGeometry.RADIUS;
        assertEquals(DanmakuForm.NEEDLE, DanmakuWheelGeometry.pick(42, -42, radius));
        assertEquals(DanmakuForm.FLOWER, DanmakuWheelGeometry.pick(43, -42, radius));
    }
}
