package com.maidweapon.forge.system.danmaku;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class DanmakuGrowthTest {
    @Test void twoSecondAutoCadenceAndLevelProgression() {
        assertEquals(2, DanmakuGrowth.auto(1).count());
        assertEquals(3, DanmakuGrowth.auto(2).count());
        assertEquals(3, DanmakuGrowth.auto(8).count());
        assertEquals(4, DanmakuGrowth.auto(8, true).count());
        assertEquals(3, DanmakuGrowth.auto(7, true).count());
        assertEquals(40, DanmakuGrowth.auto(5).cooldownTicks());
        for (int level = 1; level <= 10; level++) {
            var auto = DanmakuGrowth.auto(level);
            assertTrue(auto.bonusFraction() <= .20);
            assertTrue(auto.count() <= 4);
        }
    }
    @Test void fourDistinctFormsHaveBoundedProjectileAndDamageBudgets() {
        assertFalse(DanmakuForm.NEEDLE.unlocked(4));
        assertTrue(DanmakuForm.NEEDLE.unlocked(5));
        assertEquals(120, DanmakuForm.NEEDLE.cooldownTicks());
        assertEquals(.45, DanmakuGrowth.fraction(DanmakuForm.NEEDLE, 5), .001);
        assertEquals(180, DanmakuForm.FLOWER.cooldownTicks());
        assertEquals(.35, DanmakuGrowth.fraction(DanmakuForm.FLOWER, 9), .001);
        assertEquals(9, DanmakuGrowth.count(DanmakuForm.FLOWER, 7));
        assertEquals(12, DanmakuGrowth.count(DanmakuForm.FLOWER, 9));
        assertEquals(5, DanmakuGrowth.count(DanmakuForm.GUARD, 5));
        assertEquals(8, DanmakuGrowth.count(DanmakuForm.GUARD, 6));
        assertEquals(320, DanmakuForm.GUARD.cooldownTicks());
        assertFalse(DanmakuForm.FANTASY.unlocked(9));
        assertTrue(DanmakuForm.FANTASY.unlocked(10));
        assertEquals(24, DanmakuGrowth.count(DanmakuForm.FANTASY, 10));
        assertEquals(800, DanmakuForm.FANTASY.cooldownTicks());
        assertEquals(14, DanmakuGrowth.waveSpacing(DanmakuForm.FANTASY));
        for (var form : DanmakuForm.values()) {
            assertTrue(DanmakuGrowth.maxProjectilesPerCast(form, 10) <= 24);
            assertEquals(0, DanmakuGrowth.resonanceCost(form, 10));
        }
    }
    @Test void invalidFormsCannotBeDecoded() {
        assertNull(DanmakuForm.safe(-1));
        assertNull(DanmakuForm.safe(4));
    }
}
