package com.maidweapon.forge.system.danmaku;

import com.maidweapon.forge.api.danmaku.DanmakuRuntimeApi;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import java.util.UUID;

/** Domain rules plugged into the neutral runtime API at bootstrap. */
public final class DanmakuRuntimeRules implements DanmakuRuntimeApi.Rules {
    @Override public void projectileVisual(ServerPlayer owner, net.minecraft.world.entity.Entity bullet,
                                           int form, boolean hit, net.minecraft.world.phys.Vec3 pos) {
        DanmakuWorldEvents.send(owner, hit ? com.maidweapon.forge.api.danmaku.DanmakuVisualEvent.Kind.HIT
                : com.maidweapon.forge.api.danmaku.DanmakuVisualEvent.Kind.PROJECTILE, form, 0, bullet, pos);
    }
    @Override public String authorizedMaid(ServerPlayer owner) {
        var context = DanmakuEligibility.context(owner);
        return context == null ? null : context.maid();
    }
    @Override public boolean canAllocate(int active, int pending, int incoming) {
        return DanmakuPerformanceBudget.capacity(active, pending, incoming);
    }
    @Override public int maximumGlobalQueue() { return DanmakuPerformanceBudget.MAX_QUEUED_GLOBAL; }
    @Override public int maximumOwnerQueue() { return DanmakuPerformanceBudget.MAX_QUEUED_PER_OWNER; }
    @Override public int maximumActiveOwner() { return DanmakuPerformanceBudget.MAX_ACTIVE_PER_OWNER; }
    @Override public boolean mayHit(UUID victim, long tick) { return DanmakuHitPolicy.mayHit(victim, tick); }
    @Override public float claim(UUID volley, UUID owner, UUID victim, long tick) {
        return DanmakuHitBudget.claim(volley, owner, victim, tick);
    }
    @Override public void record(UUID victim, long tick) { DanmakuHitPolicy.record(victim, tick); }
    @Override public int relaxFrames(LivingEntity victim) {
        return DanmakuHitPolicy.temporarilyRelaxVanillaFrames(victim);
    }
    @Override public void restoreFrames(LivingEntity victim, int previous) {
        DanmakuHitPolicy.restoreFrames(victim, previous);
    }
}
