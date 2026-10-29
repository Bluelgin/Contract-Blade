package com.maidweapon.forge.system.oath;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OathBurnTest {
    @Test void fullPoolBurnsInFortySecondsWithoutRoundingPerTick() {
        for (int capacity : new int[]{1, 7, 200, 1000, 1777, 10000}) {
            int consumed = 0; long remainder = 0;
            for (int tick = 0; tick < 800; tick++) {
                var step = OathBurn.tick(capacity, .025, remainder);
                consumed += step.cost(); remainder = step.remainder();
                if (tick < 799) assertTrue(consumed < capacity);
            }
            assertEquals(capacity, consumed); assertEquals(0, remainder);
        }
    }
    @Test void remainderSurvivesSaveAndRateChanges() {
        var before = OathBurn.tick(1000, .025, 0);
        assertEquals(1, before.cost()); assertEquals(250000, before.remainder());
        var restored = OathBurn.tick(1000, .05, before.remainder());
        assertEquals(2, restored.cost()); assertEquals(750000, restored.remainder());
    }
    @Test void penaltiesCapAtFiveWhileEffectCanContinueStacking() {
        assertEquals(1, OathBurn.penaltyLevels(0));
        assertEquals(5, OathBurn.penaltyLevels(4));
        assertEquals(5, OathBurn.penaltyLevels(99));
    }
}
