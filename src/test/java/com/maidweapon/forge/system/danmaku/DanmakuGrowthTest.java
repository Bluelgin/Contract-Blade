package com.maidweapon.forge.system.danmaku;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DanmakuGrowthTest {
    @Test void autosAlwaysAvailableAndGrowWithMastery() {
        assertEquals(3, DanmakuGrowth.auto(1).count());
        assertEquals(4, DanmakuGrowth.auto(2).count());
        assertEquals(4, DanmakuGrowth.auto(8).count());
        assertEquals(6, DanmakuGrowth.auto(8, true).count());
        assertEquals(0, DanmakuGrowth.auto(10).cooldownTicks());
        for (int i = 1; i <= 10; i++) {
            assertTrue(DanmakuGrowth.auto(i).bonusFraction() > 0);
            assertTrue(DanmakuGrowth.auto(i).count() <= 6);
        }
    }

    @Test void activeFormsHaveNoOldCooldownsButDistinctGrowth() {
        assertFalse(DanmakuForm.NEEDLE.unlocked(4));
        assertTrue(DanmakuForm.NEEDLE.unlocked(5));
        assertFalse(DanmakuForm.FANTASY.unlocked(9));
        assertTrue(DanmakuForm.FANTASY.unlocked(10));
        for (DanmakuForm form : DanmakuForm.values())
            assertEquals(0, form.cooldownTicks());
        assertEquals(12, DanmakuGrowth.count(DanmakuForm.NEEDLE, 5));
        assertEquals(9, DanmakuGrowth.count(DanmakuForm.FLOWER, 5));
        assertEquals(12, DanmakuGrowth.count(DanmakuForm.FLOWER, 7));
        assertEquals(15, DanmakuGrowth.count(DanmakuForm.FLOWER, 9));
        assertEquals(5, DanmakuGrowth.count(DanmakuForm.GUARD, 5));
        assertEquals(8, DanmakuGrowth.count(DanmakuForm.GUARD, 6));
        assertEquals(32, DanmakuGrowth.count(DanmakuForm.FANTASY, 10));
        assertEquals(32, DanmakuGrowth.maxProjectilesPerCast(DanmakuForm.FANTASY, 10));
    }

    @Test void resonanceScalesSafelyWithPoolCapacity() {
        assertEquals(80, DanmakuResonanceCostPolicy.scaled(1000, 8));
        assertEquals(140, DanmakuResonanceCostPolicy.scaled(1000, 14));
        assertEquals(220, DanmakuResonanceCostPolicy.scaled(1000, 22));
        assertEquals(450, DanmakuResonanceCostPolicy.scaled(1000, 45));
        assertEquals(1, DanmakuResonanceCostPolicy.scaled(3, 8));
        assertEquals(Integer.MAX_VALUE, DanmakuResonanceCostPolicy.scaled(0, 45));
    }

    @Test void independentHitSpacingAndRuntimeEntityLimits() {
        java.util.UUID victim = java.util.UUID.randomUUID();
        DanmakuHitPolicy.clear();
        assertTrue(DanmakuHitPolicy.mayHit(victim, 10));
        DanmakuHitPolicy.record(victim, 10);
        assertFalse(DanmakuHitPolicy.mayHit(victim, 10));
        assertFalse(DanmakuHitPolicy.mayHit(victim, 11));
        assertTrue(DanmakuHitPolicy.mayHit(victim, 12));
        assertTrue(DanmakuPerformanceBudget.capacity(30, 20, 32));
        assertFalse(DanmakuPerformanceBudget.capacity(80, 10, 10));
        assertFalse(DanmakuPerformanceBudget.capacity(0, 0, 33));
        assertFalse(DanmakuPerformanceBudget.capacity(10, 125, 12));
    }
}
