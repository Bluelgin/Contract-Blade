package com.maidweapon.forge.system.danmaku;

import java.util.*;

/**
 * One server-owned array budget: each *different* real projectile collision can
 * pay its per-shot amount, up to N shots per target and a bounded number of targets.
 * Ordinary hurt/armor/boss protections are handled by the native projectile itself.
 */
public final class DanmakuHitBudget {
    private record Volley(UUID owner, UUID lock, float each, int hitsPerTarget,
                          int maxTargets, long expiry, Map<UUID, Integer> hits) { }
    private static final Map<UUID, Volley> ACTIVE = new LinkedHashMap<>();
    private static final int MAX_VOLLEYS = 512;

    public static UUID open(UUID owner, UUID lockedTarget, float each, int shots,
                            int maxTargets, long now, int lifetime) {
        if (owner == null || !Float.isFinite(each) || each <= 0 ||
                shots < 1 || shots > 64 || maxTargets < 1 || maxTargets > 3 ||
                lifetime < 1 || lifetime > 120) return null;
        cleanup(now);
        while (ACTIVE.size() >= MAX_VOLLEYS) ACTIVE.remove(ACTIVE.keySet().iterator().next());
        UUID id = UUID.randomUUID();
        ACTIVE.put(id, new Volley(owner, lockedTarget, each, shots, maxTargets,
                now + lifetime, new HashMap<>()));
        return id;
    }

    /** Never duplicates per-shot budget or pays a missed/unauthorized projectile. */
    public static float claim(UUID volleyId, UUID owner, UUID victim, long now) {
        Volley volley = ACTIVE.get(volleyId);
        if (volley == null || now > volley.expiry || !volley.owner.equals(owner)
                || victim == null || volley.lock != null && !volley.lock.equals(victim)) return 0;
        Integer previous = volley.hits.get(victim);
        if (previous == null && volley.hits.size() >= volley.maxTargets) return 0;
        if (previous != null && previous >= volley.hitsPerTarget) return 0;
        volley.hits.put(victim, previous == null ? 1 : previous + 1);
        return volley.each;
    }

    public static void cleanup(long now) { ACTIVE.entrySet().removeIf(e -> now > e.getValue().expiry); }
    public static void clear() { ACTIVE.clear(); }
    private DanmakuHitBudget() { }
}
