package com.maidweapon.forge.system.danmaku;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DanmakuGrowthTest {
    @Test void progressiveAutoAndNoBulletCountDamageExplosion() {
        assertEquals(2, DanmakuGrowth.auto(1).count());
        assertEquals(3, DanmakuGrowth.auto(2).count());
        assertEquals(4, DanmakuGrowth.auto(8).count());
        assertTrue(DanmakuGrowth.auto(10).bonusFraction() <= .20);
        for (int level = 1; level <= 10; level++) {
            var auto = DanmakuGrowth.auto(level);
            assertTrue(auto.count() * (auto.bonusFraction() / auto.count()) <= .201);
        }
    }
    @Test void lateActiveFormsStayLocked() {
        assertFalse(DanmakuForm.NEEDLE.unlocked(4));
        assertTrue(DanmakuForm.NEEDLE.unlocked(5));
        assertFalse(DanmakuForm.GUARD.unlocked(5));
        assertTrue(DanmakuForm.GUARD.unlocked(6));
        assertFalse(DanmakuForm.FANTASY.unlocked(9));
        assertTrue(DanmakuForm.FANTASY.unlocked(10));
    }
    @Test void resourcePaymentIntentionallyDeferred() {
        for (var form : DanmakuForm.values()) {
            assertEquals(0, DanmakuGrowth.resonanceCost(form, 10));
            assertTrue(form.cooldownTicks() >= 120);
        }
        assertNull(DanmakuForm.safe(-1));
        assertNull(DanmakuForm.safe(4));
    }
}
