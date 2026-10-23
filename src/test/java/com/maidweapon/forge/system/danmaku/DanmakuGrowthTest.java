package com.maidweapon.forge.system.danmaku;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DanmakuGrowthTest {
    @Test void fixedAutoSalvosAreMasteryIndependent() {
        for (int level = 0; level <= 10; level++) {
            assertEquals(4, DanmakuGrowth.auto(level).count());
            assertEquals(6, DanmakuGrowth.auto(level, true).count());
            assertEquals(.12, DanmakuGrowth.auto(level).bonusFraction(), 1e-9);
            assertEquals(5, DanmakuGrowth.auto(level).cooldownTicks());
            for (var form : DanmakuForm.values()) assertTrue(form.unlocked(level));
        }
    }
    @Test void allFormsUseFixedStrengthAndKeepShortIntervals() {
        assertEquals(8, DanmakuForm.NEEDLE.cooldownTicks());
        assertEquals(12, DanmakuForm.FLOWER.cooldownTicks());
        assertEquals(10, DanmakuForm.GUARD.cooldownTicks());
        assertEquals(20, DanmakuForm.FANTASY.cooldownTicks());
        for (int level = 0; level <= 10; level++) {
            assertEquals(12, DanmakuGrowth.count(DanmakuForm.NEEDLE, level));
            assertEquals(12, DanmakuGrowth.count(DanmakuForm.FLOWER, level));
            assertEquals(6, DanmakuGrowth.count(DanmakuForm.GUARD, level));
            assertEquals(32, DanmakuGrowth.count(DanmakuForm.FANTASY, level));
            assertEquals(.84, DanmakuGrowth.fraction(DanmakuForm.FLOWER, level), 1e-9);
            assertEquals(.20, DanmakuGrowth.fraction(DanmakuForm.GUARD, level), 1e-9);
            assertEquals(24, DanmakuGrowth.maxProjectilesPerCast(DanmakuForm.FLOWER, level));
            assertTrue(DanmakuGrowth.waveAllowed(DanmakuForm.FANTASY, level, 3));
            assertFalse(DanmakuGrowth.waveAllowed(DanmakuForm.FANTASY, level, 4));
        }
    }

    /** Keep the 2-tick budget victim-global even with multiple owners: no stacking bypass. */
    @Test void concurrentOwnersCannotBypassSharedVictimHitWindow() {
        java.util.UUID victim = java.util.UUID.randomUUID();
        DanmakuHitPolicy.clear();
        assertTrue(DanmakuHitPolicy.mayHit(victim, 200));
        DanmakuHitPolicy.record(victim, 200);
        assertFalse(DanmakuHitPolicy.mayHit(victim, 201));
        assertTrue(DanmakuHitPolicy.mayHit(victim, 202));
        DanmakuHitPolicy.clear();
    }

    @Test void resonanceScalesSafelyWithPoolCapacity() {
        assertEquals(60, DanmakuResonanceCostPolicy.scaled(1000, 6));
        assertEquals(100, DanmakuResonanceCostPolicy.scaled(1000, 10));
        assertEquals(160, DanmakuResonanceCostPolicy.scaled(1000, 16));
        assertEquals(320, DanmakuResonanceCostPolicy.scaled(1000, 32));
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
