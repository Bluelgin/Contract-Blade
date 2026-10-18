package com.maidweapon.forge.system.danmaku;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class DanmakuHitBudgetTest {
    @AfterEach void clear() { DanmakuHitBudget.clear(); }

    @Test void oneLegitimateHitPerTargetAndPerWave() {
        UUID owner = UUID.randomUUID(), enemy = UUID.randomUUID();
        UUID id = DanmakuHitBudget.open(owner, enemy, 6f, 1, 200, 45);
        assertNotNull(id);
        assertEquals(0f, DanmakuHitBudget.claim(id, UUID.randomUUID(), enemy, 202));
        assertEquals(6f, DanmakuHitBudget.claim(id, owner, enemy, 202));
        assertEquals(0f, DanmakuHitBudget.claim(id, owner, enemy, 203));
    }
    @Test void cannotStealLockedTargetCreditAndNoFreeDamageAfterExpiry() {
        UUID owner = UUID.randomUUID(), target = UUID.randomUUID();
        UUID volley = DanmakuHitBudget.open(owner, target, 3.5f, 1, 100, 20);
        assertEquals(0f, DanmakuHitBudget.claim(volley, owner, UUID.randomUUID(), 103));
        assertEquals(0f, DanmakuHitBudget.claim(volley, owner, target, 121));
    }
    @Test void floweringPerTargetCapBoundsAoE() {
        UUID owner = UUID.randomUUID();
        UUID volley = DanmakuHitBudget.open(owner, null, 2f, 3, 50, 50);
        for (int i = 0; i < 3; i++) assertEquals(2f,
                DanmakuHitBudget.claim(volley, owner, UUID.randomUUID(), 51));
        assertEquals(0f, DanmakuHitBudget.claim(volley, owner, UUID.randomUUID(), 51));
    }
    @Test void rejectsInvalidBudgets() {
        UUID owner = UUID.randomUUID();
        assertNull(DanmakuHitBudget.open(owner, null, -1f, 1, 0, 20));
        assertNull(DanmakuHitBudget.open(owner, null, Float.NaN, 1, 0, 20));
        assertNull(DanmakuHitBudget.open(owner, null, 1f, 8, 0, 20));
    }
}
