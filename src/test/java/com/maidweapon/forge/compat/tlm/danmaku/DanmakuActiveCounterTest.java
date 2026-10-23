package com.maidweapon.forge.compat.tlm.danmaku;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class DanmakuActiveCounterTest {
    @AfterEach void cleanup() { DanmakuActiveCounter.clear(); }

    @Test void freshlySpawnedBulletsIncrementTheSameTickBudget() {
        UUID id = UUID.randomUUID();
        assertEquals(-1, DanmakuActiveCounter.current(id, "minecraft:overworld", 40));
        DanmakuActiveCounter.record(id, "minecraft:overworld", 40, 94);
        DanmakuActiveCounter.spawned(id, "minecraft:overworld", 40);
        assertEquals(95, DanmakuActiveCounter.current(id, "minecraft:overworld", 40));
        DanmakuActiveCounter.spawned(id, "minecraft:overworld", 40);
        assertEquals(96, DanmakuActiveCounter.current(id, "minecraft:overworld", 40));
        assertEquals(-1, DanmakuActiveCounter.current(id, "minecraft:overworld", 41));
    }

    @Test void worldSwitchRollbackAndLogoutInvalidateTheCachedCount() {
        UUID id = UUID.randomUUID();
        DanmakuActiveCounter.record(id, "minecraft:overworld", 40, 10);
        assertEquals(-1, DanmakuActiveCounter.current(id, "minecraft:the_nether", 40));
        assertEquals(-1, DanmakuActiveCounter.current(id, "minecraft:overworld", 39));
        DanmakuActiveCounter.forget(id);
        assertEquals(-1, DanmakuActiveCounter.current(id, "minecraft:overworld", 40));
    }
}
