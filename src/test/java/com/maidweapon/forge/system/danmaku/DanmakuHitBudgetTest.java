package com.maidweapon.forge.system.danmaku;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class DanmakuHitBudgetTest {
    @AfterEach void reset() { DanmakuHitBudget.clear(); DanmakuHitPolicy.clear(); }

    @Test void everyDistinctProjectileMayPayItsOwnIndependentDamage() {
        var owner = UUID.randomUUID();
        var victim = UUID.randomUUID();
        UUID volley = DanmakuHitBudget.open(owner, victim, 2.5f, 6, 1, 200, 120);
        assertNotNull(volley);
        for (int i = 0; i < 6; i++) {
            long tick = 200 + i * 2;
            assertTrue(DanmakuHitPolicy.mayHit(victim, tick));
            assertEquals(2.5f, DanmakuHitBudget.claim(volley, owner, victim, tick));
            DanmakuHitPolicy.record(victim, tick);
            assertFalse(DanmakuHitPolicy.mayHit(victim, tick + 1));
        }
        assertEquals(0f, DanmakuHitBudget.claim(volley, owner, victim, 230));
    }

    @Test void locksAreServerOwnedAndRequireARealMatchingTarget() {
        UUID owner = UUID.randomUUID(), enemy = UUID.randomUUID();
        UUID volley = DanmakuHitBudget.open(owner, enemy, 3f, 4, 1, 100, 50);
        assertEquals(0, DanmakuHitBudget.claim(volley, UUID.randomUUID(), enemy, 104));
        assertEquals(0, DanmakuHitBudget.claim(volley, owner, UUID.randomUUID(), 104));
        assertEquals(0, DanmakuHitBudget.claim(volley, owner, enemy, 151));
    }

    @Test void flowerCanDamageThreeTargetsButNeverAnUnlimitedFourth() {
        UUID owner = UUID.randomUUID();
        UUID volley = DanmakuHitBudget.open(owner, null, 1.5f, 12, 3, 100, 50);
        UUID[] targets = {UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()};
        for (UUID victim : targets) {
            for (int i = 0; i < 12; i++) {
                assertEquals(1.5f, DanmakuHitBudget.claim(volley, owner, victim, 101 + i * 2));
            }
            assertEquals(0f, DanmakuHitBudget.claim(volley, owner, victim, 149));
        }
        assertEquals(0f, DanmakuHitBudget.claim(volley, owner, UUID.randomUUID(), 111));
    }

    @Test void rejectsImpossibleOrUnboundedVolleyParameters() {
        UUID owner = UUID.randomUUID();
        assertNull(DanmakuHitBudget.open(owner, null, -1, 3, 1, 0, 90));
        assertNull(DanmakuHitBudget.open(owner, null, Float.NaN, 3, 1, 0, 90));
        assertNull(DanmakuHitBudget.open(owner, null, 3, 128, 1, 0, 90));
        assertNull(DanmakuHitBudget.open(owner, null, 3, 2, 5, 0, 90));
    }
}
