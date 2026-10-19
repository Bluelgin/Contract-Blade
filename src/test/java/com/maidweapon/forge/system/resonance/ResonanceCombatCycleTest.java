package com.maidweapon.forge.system.resonance;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ResonanceCombatCycleTest {
    @Test void manifestationOverridesIdleAbilityButCombatRetainsLiveRecovery() {
        for (var activity : ResonanceActivity.values()) {
            boolean combat = activity.ordinal() >= ResonanceActivity.COMBAT.ordinal();
            assertEquals(combat ? 10 : 20, ResonanceRecoveryPolicy.rate(true, activity, 2.5, 20, 2.5, 0, 10));
            assertEquals(combat ? 0 : 2.5, ResonanceRecoveryPolicy.rate(false, activity, 2.5, 20, 2.5, 0, 10));
        }
    }

    @Test void zeroToRecoveryCycleAndSpiritVeinTiming() {
        for (double multiplier : new double[]{1, 1.25, 1.6, 2}) {
            var accumulator = new ResonanceAccumulator();
            int balance = 100;
            for (int tick = 1; tick <= 600; tick++)
                balance += accumulator.tick(tick, ResonanceRecoveryPolicy.perTick(20, multiplier), balance, 1000);
            assertEquals(Math.min(1000, 100 + (int)(600 * multiplier)), balance);
        }
    }

    @Test void allTargetsShareHalfSecondPaymentIncludingExactBoundary() {
        var window = new ResonancePaymentWindow();
        var payments = new AtomicInteger();
        for (int tick = 0; tick < 20; tick++) for (int target = 0; target < 8; target++)
            assertTrue(window.pay(tick, 10, () -> { payments.incrementAndGet(); return true; }));
        assertEquals(2, payments.get());
        assertFalse(window.covers(20));
        assertTrue(window.pay(20, 10, () -> { payments.incrementAndGet(); return true; }));
        assertEquals(3, payments.get());
    }

    @Test void failedPaymentClockRewindAndSeparateContractsNeverGrantFreeAttacks() {
        var window = new ResonancePaymentWindow();
        assertFalse(window.pay(100, 20, () -> false));
        assertFalse(window.covers(100));
        assertTrue(window.pay(100, 20, () -> true));
        assertFalse(window.covers(99));
        assertFalse(new ResonancePaymentWindow().covers(101));
        assertFalse(window.pay(120, 20, () -> false));
    }

    @Test void idleTimeDoesNotPrepayOrAccumulateFutureCharges() {
        var window = new ResonancePaymentWindow();
        var payments = new AtomicInteger();
        for (long tick : new long[]{10, 10000, 10001})
            window.pay(tick, 20, () -> { payments.incrementAndGet(); return true; });
        assertEquals(2, payments.get());
    }
}
