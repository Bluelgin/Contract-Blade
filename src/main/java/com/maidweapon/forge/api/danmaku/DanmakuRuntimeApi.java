package com.maidweapon.forge.api.danmaku;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import java.util.Objects;
import java.util.UUID;

/** Narrow, fail-closed server callback API for native TLM projectile adapters. */
public final class DanmakuRuntimeApi {
    public interface Rules {
        String authorizedMaid(ServerPlayer player);
        default void projectileVisual(ServerPlayer owner, net.minecraft.world.entity.Entity bullet,
                                      int form, boolean hit, net.minecraft.world.phys.Vec3 pos) { }
        boolean canAllocate(int active, int pending, int incoming);
        int maximumGlobalQueue();
        int maximumOwnerQueue();
        int maximumActiveOwner();
        boolean mayHit(UUID victim, long tick);
        float claim(UUID volley, UUID owner, UUID victim, long tick);
        void record(UUID victim, long tick);
        int relaxFrames(LivingEntity victim);
        void restoreFrames(LivingEntity victim, int previous);
    }
    public static void projectileVisual(ServerPlayer owner, net.minecraft.world.entity.Entity bullet,
                                        int form, boolean hit, net.minecraft.world.phys.Vec3 pos) {
        if (rules != null) rules.projectileVisual(owner, bullet, form, hit, pos);
    }
    private static Rules rules;
    public static void install(Rules authority) {
        if (rules != null) throw new IllegalStateException("Danmaku rules already installed");
        rules = Objects.requireNonNull(authority);
    }
    public static String authorizedMaid(ServerPlayer player) {
        return rules == null ? null : rules.authorizedMaid(player);
    }
    public static boolean canAllocate(int active, int pending, int incoming) {
        return rules != null && rules.canAllocate(active, pending, incoming);
    }
    public static int maximumGlobalQueue() {
        return rules == null ? 0 : rules.maximumGlobalQueue();
    }
    public static int maximumOwnerQueue() {
        return rules == null ? 0 : rules.maximumOwnerQueue();
    }
    public static int maximumActiveOwner() {
        return rules == null ? 0 : rules.maximumActiveOwner();
    }
    public static boolean mayHit(UUID victim, long tick) {
        return rules != null && rules.mayHit(victim, tick);
    }
    public static float claim(UUID volley, UUID owner, UUID victim, long tick) {
        return rules == null ? 0 : rules.claim(volley, owner, victim, tick);
    }
    public static void record(UUID victim, long tick) {
        if (rules != null) rules.record(victim, tick);
    }
    public static int relaxFrames(LivingEntity victim) {
        return rules == null ? victim.invulnerableTime : rules.relaxFrames(victim);
    }
    public static void restoreFrames(LivingEntity victim, int previous) {
        if (rules != null) rules.restoreFrames(victim, previous);
    }
    private DanmakuRuntimeApi() { }
}
