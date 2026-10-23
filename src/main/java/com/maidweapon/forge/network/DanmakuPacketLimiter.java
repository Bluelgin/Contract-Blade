package com.maidweapon.forge.network;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Pure policy used only on the server's enqueued work thread. */
final class DanmakuPacketLimiter {
    private static final long MIN_NANOS = 50_000_000L;
    private final Map<UUID, Long> last = new HashMap<>();
    boolean allow(UUID sender, long now) {
        if (sender == null) return false;
        Long previous = last.get(sender);
        if (previous != null && now >= previous && now - previous < MIN_NANOS) return false;
        last.put(sender, now);
        return true;
    }
    void forget(UUID sender) { last.remove(sender); }
    void clear() { last.clear(); }
}
