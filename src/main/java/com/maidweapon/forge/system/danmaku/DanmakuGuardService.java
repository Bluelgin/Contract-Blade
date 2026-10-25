package com.maidweapon.forge.system.danmaku;

import com.maidweapon.forge.system.mastery.ContractMasteryService;

import com.maidweapon.forge.compat.tlm.danmaku.ContractDanmakuProjectile;
import com.maidweapon.forge.compat.tlm.danmaku.DanmakuProjectilePatterns;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import java.util.*;
import static com.maidweapon.forge.system.danmaku.DanmakuTargeting.*;

/** Guard-only temporary state and hostile-projectile interception. */
final class DanmakuGuardService {
    private static final class GuardSession {
        final String maid;
        final ResourceKey<Level> dimension;
        final int mastery;
        final float attack;
        final long expires;
        int remaining;
        UUID aggressor;
        GuardSession(String maid, ResourceKey<Level> dimension, int mastery,
                     float attack, long expires, int remaining) {
            this.maid = maid; this.dimension = dimension;
            this.mastery = mastery; this.attack = attack;
            this.expires = expires; this.remaining = remaining;
        }
    }
    private static final Map<UUID, GuardSession> GUARDS = new HashMap<>();
    static boolean hasSession(ServerPlayer player) { return GUARDS.containsKey(player.getUUID()); }
    static boolean active(ServerPlayer player, long now) {
        GuardSession existing = GUARDS.get(player.getUUID());
        return existing != null && now < existing.expires;
    }
    static boolean overBudget() { return GUARDS.size() > 128; }
    static void start(ServerPlayer player, String maid, int level, float basis, long now) {
        GUARDS.put(player.getUUID(), new GuardSession(maid, player.level().dimension(),
                level, basis, now + 60, DanmakuGrowth.count(DanmakuForm.GUARD, level)));
    }
    static void tick(ServerPlayer player, String maid, long now) {
        GuardSession guard = GUARDS.get(player.getUUID());
        if (guard == null) return;
        if (maid == null || !guard.maid.equals(maid)
                || !guard.dimension.equals(player.level().dimension())
                || ContractMasteryService.getLevel(player) < DanmakuForm.GUARD.requiredLevel()) {
            GUARDS.remove(player.getUUID());
            DanmakuWorldEvents.send(player, com.maidweapon.forge.api.danmaku.DanmakuVisualEvent.Kind.GUARD_STOP,
                    2, 0, player, player.position());
            return;
        }
        if (now >= guard.expires || guard.remaining <= 0) {
            GUARDS.remove(player.getUUID());
            DanmakuWorldEvents.send(player, com.maidweapon.forge.api.danmaku.DanmakuVisualEvent.Kind.GUARD_STOP,
                    2, 0, player, player.position());
            LivingEntity retaliation = targetById(player, guard.aggressor);
            if (retaliation == null) retaliation = acquireTarget(player);
            DanmakuProjectilePatterns.guardCounter(player, retaliation, guard.mastery, guard.attack);
            return;
        }
        if (player.tickCount % 2 != 0) return;
        // Prevent indiscriminate projectile deletion: intercept only living hostile
        // shooters, never the player's projectiles, maid shots or ownerless effects.
        AABB zone = player.getBoundingBox().inflate(3.25);
        List<Projectile> threats = player.level().getEntitiesOfClass(Projectile.class, zone,
                projectile -> !projectile.isRemoved()
                        && projectile.getDeltaMovement().lengthSqr() > .0025
                        && projectile.getOwner() instanceof LivingEntity shooter
                        && ContractDanmakuProjectile.safeTarget(player, shooter));
        for (Projectile shot : threats) {
            if (guard.remaining <= 0) break;
            Entity origin = shot.getOwner();
            guard.aggressor = origin.getUUID();
            shot.discard();
            guard.remaining--;
            DanmakuWorldEvents.send(player, com.maidweapon.forge.api.danmaku.DanmakuVisualEvent.Kind.INTERCEPT,
                    2, 0, shot, shot.position());
            ((ServerLevel) player.level()).sendParticles(ParticleTypes.ENCHANT,
                    shot.getX(), shot.getY(), shot.getZ(), 5, .15, .15, .15, .08);
        }
        if (player.tickCount % 6 == 0) {
            double theta = now * .13;
            ((ServerLevel) player.level()).sendParticles(ParticleTypes.ENCHANT,
                    player.getX() + Math.cos(theta) * 2,
                    player.getY() + 1.2, player.getZ() + Math.sin(theta) * 2,
                    2, .18, .30, .18, .015);
        }
    }
    static void forget(UUID player) { GUARDS.remove(player); }
    static void clear() { GUARDS.clear(); }
    private DanmakuGuardService() { }
}
