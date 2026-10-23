package com.maidweapon.forge.network;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class DanmakuPacketLimiterTest {
    @Test void perSenderThrottlesExactlyOncePerWindow() {
        var limiter = new DanmakuPacketLimiter();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        assertTrue(limiter.allow(a, 1_000_000_000L));
        assertFalse(limiter.allow(a, 1_049_999_999L));
        assertTrue(limiter.allow(b, 1_049_999_999L));
        assertTrue(limiter.allow(a, 1_050_000_000L));
        assertFalse(limiter.allow(a, 1_050_000_000L));
    }
    @Test void disconnectAndClockRollbackDoNotLockPlayerOut() {
        var limiter = new DanmakuPacketLimiter();
        UUID a = UUID.randomUUID();
        assertFalse(limiter.allow(null, 0L));
        assertTrue(limiter.allow(a, 100_000_000L));
        assertTrue(limiter.allow(a, 20L));
        assertFalse(limiter.allow(a, 21L));
        limiter.forget(a);
        assertTrue(limiter.allow(a, 21L));
        limiter.clear();
        assertTrue(limiter.allow(a, 21L));
    }
}
