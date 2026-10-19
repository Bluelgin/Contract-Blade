package com.maidweapon.forge.compat.tlm.danmaku;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** A per-player, per-world, per-tick conservative counter for our own spawned projectiles. */
final class DanmakuActiveCounter {
    private record Sample(String dimension, long tick, int count) { }
    private static final Map<UUID, Sample> SAMPLES = new HashMap<>();

    static int current(UUID owner, String dimension, long tick) {
        Sample sample = SAMPLES.get(owner);
        return sample != null && sample.tick() == tick && sample.dimension().equals(dimension)
                ? sample.count() : -1;
    }
    static void record(UUID owner, String dimension, long tick, int count) {
        SAMPLES.put(owner, new Sample(dimension, tick, Math.max(0, count)));
    }
    static void spawned(UUID owner, String dimension, long tick) {
        int count = current(owner, dimension, tick);
        if (count >= 0) record(owner, dimension, tick, count + 1);
    }
    static void forget(UUID owner) { SAMPLES.remove(owner); }
    static void clear() { SAMPLES.clear(); }
    private DanmakuActiveCounter() { }
}
