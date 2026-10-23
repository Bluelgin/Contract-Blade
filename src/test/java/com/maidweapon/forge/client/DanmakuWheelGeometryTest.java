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
    @Test void exportedIconCentersAlwaysPickMatchingForm() {
        for (int[] size : new int[][]{{320,240},{640,360},{1920,1080},{160,120}}) {
            int r=DanmakuWheelGeometry.fitRadius(size[0],size[1]);
            int d=DanmakuWheelGeometry.iconOffset(r);
            assertEquals(DanmakuForm.NEEDLE,DanmakuWheelGeometry.pick(0,-d,r));
            assertEquals(DanmakuForm.FLOWER,DanmakuWheelGeometry.pick(d,0,r));
            assertEquals(DanmakuForm.GUARD,DanmakuWheelGeometry.pick(-d,0,r));
            assertEquals(DanmakuForm.FANTASY,DanmakuWheelGeometry.pick(0,d,r));
        }
    }
}
