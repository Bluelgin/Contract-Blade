package com.maidweapon.forge.system.danmaku;

import net.minecraft.world.entity.LivingEntity;
import java.util.*;

/**
 * A short, target-local vanilla immunity adaptation for contract bullets ONLY.
 * No globally reduced iframes, health edits, invulnerability flags or boss phase
 * changes.  Two valid projectile attempts against one mob must be 2 game ticks apart.
 */
public final class DanmakuHitPolicy {
    public static final int MIN_TICKS = 2;
    private static final Map<UUID, Long> LAST = new HashMap<>();

    public static boolean mayHit(UUID victim, long tick) {
        Long previous = LAST.get(victim);
        return previous == null || tick < previous || tick - previous >= MIN_TICKS;
    }

    /** Called only for an actual valid collision on the server. */
    public static void record(UUID victim, long tick) { LAST.put(victim, tick); }

    public static int temporarilyRelaxVanillaFrames(LivingEntity target) {
        int original = target.invulnerableTime;
        // Standard post-hit vanilla iframe gate, not isInvulnerable or mod-specific flags.
        if (original > 10) target.invulnerableTime = 0;
        return original;
    }

    public static void restoreFrames(LivingEntity target, int original) {
        // Preserve prior ordinary protection for all other attack sources.
        if (original > 10) target.invulnerableTime = Math.max(original, target.invulnerableTime);
    }

    public static void cleanup(long tick) {
        LAST.entrySet().removeIf(e -> tick < e.getValue() || tick - e.getValue() > 100);
    }
    public static void clear() { LAST.clear(); }
    private DanmakuHitPolicy() { }
}
