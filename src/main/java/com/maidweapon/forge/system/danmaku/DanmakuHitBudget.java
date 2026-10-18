package com.maidweapon.forge.system.danmaku;

import java.util.*;

/**
 * Server-only, actual-hit volley settlement. A volley may pay each permitted target
 * once: many projectiles cannot multiply its intended damage. No damage is caused
 * here; the hitting TLM projectile still uses its normal hurt path.
 */
public final class DanmakuHitBudget {
    private record Volley(UUID owner, UUID lock, float amount, int targetLimit,
                          long expiresAt, Set<UUID> paid) { }
    private static final Map<UUID, Volley> ACTIVE = new LinkedHashMap<>();
    private static final int MAX_ACTIVE = 256;

    public static UUID open(UUID owner, UUID lockedTarget, float damage,
                            int targets, long now, int lifetime) {
        if (owner == null || !Float.isFinite(damage) || damage <= 0
                || targets < 1 || targets > 4 || lifetime < 1 || lifetime > 120) return null;
        cleanup(now);
        while (ACTIVE.size() >= MAX_ACTIVE) ACTIVE.remove(ACTIVE.keySet().iterator().next());
        UUID id = UUID.randomUUID();
        ACTIVE.put(id, new Volley(owner, lockedTarget, damage, targets,
                now + lifetime, new HashSet<>()));
        return id;
    }

    /** Called exclusively after a real safe projectile collision. */
    public static float claim(UUID id, UUID owner, UUID victim, long now) {
        Volley volley = ACTIVE.get(id);
        if (volley == null || now > volley.expiresAt || !volley.owner.equals(owner)
                || victim == null || volley.lock != null && !volley.lock.equals(victim)
                || volley.paid.contains(victim) || volley.paid.size() >= volley.targetLimit)
            return 0;
        volley.paid.add(victim);
        if (volley.paid.size() >= volley.targetLimit) ACTIVE.remove(id);
        return volley.amount;
    }

    public static void cleanup(long now) {
        ACTIVE.entrySet().removeIf(e -> now > e.getValue().expiresAt);
    }
    public static void clear() { ACTIVE.clear(); }
    private DanmakuHitBudget() { }
}
