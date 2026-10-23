package com.maidweapon.forge.system.danmaku;

import net.minecraft.server.level.ServerPlayer;
import java.util.*;

/** Server-thread-only, bounded per-session input and spell cooldown state. */
final class DanmakuInputGate {
    private static final Map<UUID, Long> LAST_CAST_TICK = new HashMap<>();
    private static final Map<UUID, Long> LAST_ATTACK_TICK = new HashMap<>();
    private static final Map<String, Long> LAST_FORM_CAST_TICK = new HashMap<>();

    static boolean attackReady(UUID player, long now) {
        Long last = LAST_ATTACK_TICK.get(player);
        return last == null || now < last || now - last >= 5;
    }
    static void markAttack(UUID player, long now) { LAST_ATTACK_TICK.put(player, now); }

    static boolean castReady(ServerPlayer player, String maid, DanmakuForm form, long now) {
        if (LAST_CAST_TICK.getOrDefault(player.getUUID(), -1L) == now) return false;
        Long previous = LAST_FORM_CAST_TICK.get(key(player, maid, form));
        return previous == null || now < previous || now - previous >= form.cooldownTicks();
    }
    static void markCast(ServerPlayer player, String maid, DanmakuForm form, long now) {
        LAST_CAST_TICK.put(player.getUUID(), now);
        LAST_FORM_CAST_TICK.put(key(player, maid, form), now);
    }
    private static String key(ServerPlayer player, String maid, DanmakuForm form) {
        return player.getUUID() + ":" + maid + ":cast_" + form.name();
    }
    static void forget(UUID player) {
        LAST_CAST_TICK.remove(player);
        LAST_ATTACK_TICK.remove(player);
        LAST_FORM_CAST_TICK.keySet().removeIf(k -> k.startsWith(player + ":"));
    }
    static void clear() { LAST_CAST_TICK.clear(); LAST_ATTACK_TICK.clear(); LAST_FORM_CAST_TICK.clear(); }
    private DanmakuInputGate() { }
}
