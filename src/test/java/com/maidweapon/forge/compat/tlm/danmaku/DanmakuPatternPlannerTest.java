package com.maidweapon.forge.compat.tlm.danmaku;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DanmakuPatternPlannerTest {
    @Test void floralWavesAreBoundedAndDistinct() {
        for (int count : new int[]{9, 12, 15}) {
            boolean changed = false;
            for (int i = 0; i < count; i++) {
                var bloom = DanmakuPatternPlanner.flower(i, count, 0, 9);
                var closing = DanmakuPatternPlanner.flower(i, count, 1, 9);
                assertTrue(Double.isFinite(bloom.yaw()));
                assertTrue(Math.abs(bloom.yaw()) <= .70);
                assertTrue(Math.abs(closing.yaw()) <= .40);
                assertTrue(bloom.speed() > 0 && closing.speed() > 0);
                changed |= Math.abs(bloom.yaw() - closing.yaw()) > .001;
            }
            assertTrue(changed);
        }
        assertThrows(IllegalArgumentException.class,
                () -> DanmakuPatternPlanner.flower(15, 15, 0, 9));
    }
    @Test void fantasyHasFourShrinkingPetalRings() {
        for (int shot = 0; shot < 8; shot++) {
            double previous = Double.POSITIVE_INFINITY;
            for (int wave = 0; wave < 4; wave++) {
                double radius = DanmakuPatternPlanner.fantasyRadius(wave, shot);
                assertTrue(radius > 1.0 && radius <= 4.2);
                assertTrue(radius < previous);
                previous = radius;
            }
        }
        assertThrows(IllegalArgumentException.class,
                () -> DanmakuPatternPlanner.fantasyAngle(4, 0));
    }
}
