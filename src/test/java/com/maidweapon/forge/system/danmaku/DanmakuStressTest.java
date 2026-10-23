package com.maidweapon.forge.system.danmaku;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Synthetic CPU/heap boundary checks; not a substitute for real Minecraft TPS benchmarks. */
class DanmakuStressTest {
    @AfterEach void reset() { DanmakuHitBudget.clear(); DanmakuHitPolicy.clear(); }

    @Test void highDensityIndependentProjectilesRemainBudgeted() {
        UUID owner = UUID.randomUUID();
        for (int volley = 0; volley < 512; volley++) {
            UUID victim = UUID.randomUUID();
            long now = volley * 5L;
            UUID id = DanmakuHitBudget.open(owner, victim, 1.0f, 32, 1, now, 120);
            assertNotNull(id);
            for (int shot = 0; shot < 32; shot++) {
                long tick = now + shot * 2L;
                assertTrue(DanmakuHitPolicy.mayHit(victim, tick));
                assertEquals(1.0f, DanmakuHitBudget.claim(id, owner, victim, tick));
                DanmakuHitPolicy.record(victim, tick);
                assertFalse(DanmakuHitPolicy.mayHit(victim, tick + 1));
            }
            assertEquals(0.0f, DanmakuHitBudget.claim(id, owner, victim, now + 90));
            if (volley % 40 == 0) DanmakuHitPolicy.cleanup(now);
        }
    }

    @Test void capacityExhaustiveContract() {
        for (int active = 0; active <= 96; active += 2)
            for (int pending = 0; pending <= 128; pending += 2)
                for (int incoming = 1; incoming <= 33; incoming++) {
                    boolean expected = incoming <= 32 && active + pending + incoming <= 96
                            && pending + incoming <= 128;
                    assertEquals(expected, DanmakuPerformanceBudget.capacity(active, pending, incoming));
                }
    }
}
