package com.maidweapon.forge.system.danmaku;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DanmakuGrowthTest {
    @Test void autosAlwaysAvailableAndGrowWithMastery() {
        assertEquals(3, DanmakuGrowth.auto(1).count());
        assertEquals(4, DanmakuGrowth.auto(2).count());
        assertEquals(4, DanmakuGrowth.auto(8).count());
        assertEquals(6, DanmakuGrowth.auto(8, true).count());
        assertEquals(5, DanmakuGrowth.auto(10).cooldownTicks());
        for (int i = 1; i <= 10; i++) {
            assertTrue(DanmakuGrowth.auto(i).bonusFraction() > 0);
            assertTrue(DanmakuGrowth.auto(i).count() <= 6);
        }
    }

    @Test void activeFormsKeepBriefServerSideIntervals() {
        assertFalse(DanmakuForm.NEEDLE.unlocked(2));
        assertTrue(DanmakuForm.NEEDLE.unlocked(3));
        assertFalse(DanmakuForm.FLOWER.unlocked(4));
        assertTrue(DanmakuForm.FLOWER.unlocked(5));
        assertFalse(DanmakuForm.GUARD.unlocked(5));
        assertTrue(DanmakuForm.GUARD.unlocked(6));
        assertFalse(DanmakuForm.FANTASY.unlocked(9));
        assertTrue(DanmakuForm.FANTASY.unlocked(10));
        assertEquals(8, DanmakuForm.NEEDLE.cooldownTicks());
        assertEquals(12, DanmakuForm.FLOWER.cooldownTicks());
        assertEquals(10, DanmakuForm.GUARD.cooldownTicks());
        assertEquals(20, DanmakuForm.FANTASY.cooldownTicks());
        assertEquals(12, DanmakuGrowth.count(DanmakuForm.NEEDLE, 5));
        assertEquals(9, DanmakuGrowth.count(DanmakuForm.FLOWER, 5));
        assertEquals(12, DanmakuGrowth.count(DanmakuForm.FLOWER, 7));
        assertEquals(15, DanmakuGrowth.count(DanmakuForm.FLOWER, 9));
        assertEquals(5, DanmakuGrowth.count(DanmakuForm.GUARD, 5));
        assertEquals(5, DanmakuGrowth.count(DanmakuForm.GUARD, 6));
        assertEquals(8, DanmakuGrowth.count(DanmakuForm.GUARD, 7));
        assertEquals(32, DanmakuGrowth.count(DanmakuForm.FANTASY, 10));
        assertEquals(32, DanmakuGrowth.maxProjectilesPerCast(DanmakuForm.FANTASY, 10));
        assertEquals(2, DanmakuGrowth.waveCount(DanmakuForm.NEEDLE));
        assertEquals(2, DanmakuGrowth.waveCount(DanmakuForm.FLOWER));
        assertEquals(0, DanmakuGrowth.waveCount(DanmakuForm.GUARD));
        assertEquals(4, DanmakuGrowth.waveCount(DanmakuForm.FANTASY));
        for (int index = 0; index < 4; index++)
            assertTrue(DanmakuGrowth.waveAllowed(DanmakuForm.FANTASY, 10, index));
        assertFalse(DanmakuGrowth.waveAllowed(DanmakuForm.FANTASY, 10, 4));
        assertFalse(DanmakuGrowth.waveAllowed(DanmakuForm.FANTASY, 9, 3));
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
