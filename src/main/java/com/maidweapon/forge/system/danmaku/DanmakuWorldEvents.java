package com.maidweapon.forge.system.danmaku;

import com.maidweapon.forge.api.danmaku.DanmakuVisualEvent;
import com.maidweapon.forge.network.DanmakuNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Bounded server-confirmed cosmetic events; combat continues if the visual budget is exhausted. */
public final class DanmakuWorldEvents {
    private static long tick = Long.MIN_VALUE;
    private static int total;
    private static final Map<UUID, Integer> COUNTS = new HashMap<>();
    public static void send(ServerPlayer p, DanmakuVisualEvent.Kind kind, int form, int phase,
                            Entity entity, Vec3 pos) {
        long now = p.serverLevel().getGameTime();
        if (tick != now) { tick = now; total = 0; COUNTS.clear(); }
        boolean cleanup = kind == DanmakuVisualEvent.Kind.CANCEL || kind == DanmakuVisualEvent.Kind.GUARD_STOP;
        int count = COUNTS.getOrDefault(p.getUUID(), 0);
        if (!cleanup && (total >= 256 || count >= 32)) return;
        if (!cleanup) { total++; COUNTS.put(p.getUUID(), count + 1); }
        var cue = new DanmakuVisualEvent(kind, p.getUUID(), entity.getUUID(),
                p.level().dimension().location(), now, form, phase, entity.getId(), pos, p.getYRot());
        DanmakuNetwork.world(p.serverLevel(), cue);
        if (Boolean.getBoolean("contractblade.danmaku.acceptance") && kind != DanmakuVisualEvent.Kind.PROJECTILE)
            org.slf4j.LoggerFactory.getLogger("MankaAcceptance").info("MANKA_VISUAL_FACT {}", cue);
    }
    private DanmakuWorldEvents() { }
}
