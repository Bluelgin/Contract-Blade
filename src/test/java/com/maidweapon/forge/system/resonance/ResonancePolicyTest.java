package com.maidweapon.forge.system.resonance;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.maidweapon.forge.system.resonance.ResonanceCostPolicy.Payment.*;

class ResonancePolicyTest {
    @Test void exactLongRunningFractionalRecovery() {
        for (double rate : new double[]{1, 2, .5}) for (double multiplier : new double[]{1, 1.25, 1.6, 2}) {
            var accumulator = new ResonanceAccumulator(); int result = 0;
            for (int tick = 0; tick < 72000; tick++) {
                result += accumulator.tick(tick, ResonanceRecoveryPolicy.perTick(rate, multiplier), result, 100000);
                assertEquals(0, accumulator.tick(tick, 9999999, result, 100000), "duplicate listener cannot settle twice");
            }
            assertEquals((int) (3600 * rate * multiplier), result);
        }
    }
    @Test void zeroCapReductionAndNoOfflineCatchup() {
        var a = new ResonanceAccumulator();
        assertEquals(1, a.tick(0, 1000000, 0, 200));
        assertEquals(1, a.tick(9000000000L, 1000000, 0, 200));
        assertEquals(0, a.tick(9000000001L, 1000000, 100, 80));
        assertEquals(1, a.tick(9000000002L, 1500000, 79, 80));
        assertEquals(0, a.tick(9000000003L, 500000, 79, 80), "surplus at cap discarded");
    }
    @Test void independentMaidRemaindersAndFreshSession() {
        var a = new ResonanceAccumulator(); var b = new ResonanceAccumulator();
        assertEquals(0, a.tick(1, 500000, 0, 200));
        assertEquals(0, b.tick(1, 500000, 0, 200));
        assertEquals(1, a.tick(2, 500000, 0, 200));
        assertEquals(0, new ResonanceAccumulator().tick(1000, 500000, 0, 200));
    }
    @Test void sequentialStagesAreNotStackable() {
        for (int current = 0; current <= 3; current++) for (int requested = 1; requested <= 4; requested++)
            assertEquals(current < 3 && requested == current + 1 ? requested : current,
                    ResonanceRecoveryPolicy.nextStage(current, requested, 3));
    }
    @Test void simulatedAbilityCostsNeverGrantAuthorization() {
        assertEquals(PAID, ResonanceCostPolicy.evaluate(true, false, 10, 10));
        assertEquals(INSUFFICIENT, ResonanceCostPolicy.evaluate(true, false, 9, 10));
        assertEquals(WAIVED, ResonanceCostPolicy.evaluate(true, true, 0, 10));
        assertEquals(UNAUTHORIZED, ResonanceCostPolicy.evaluate(false, true, 200, 10));
        assertEquals(INVALID_COST, ResonanceCostPolicy.evaluate(true, true, 200, -10));
    }
}
