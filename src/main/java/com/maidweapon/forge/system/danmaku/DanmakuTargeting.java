package com.maidweapon.forge.system.danmaku;

import com.maidweapon.forge.compat.tlm.danmaku.ContractDanmakuProjectile;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;

/** Shared target and weapon-panel facts; no casting, billing or session ownership. */
final class DanmakuTargeting {
    static LivingEntity targetById(ServerPlayer player, UUID id) {
        if (id == null) return null;
        Entity entity = player.serverLevel().getEntity(id);
        return entity instanceof LivingEntity living
                && ContractDanmakuProjectile.safeTarget(player, living) ? living : null;
    }
    static LivingEntity acquireTarget(ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (var candidate : player.serverLevel().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(24),
                victim -> ContractDanmakuProjectile.safeTarget(player, victim))) {
            Vec3 difference = candidate.getEyePosition().subtract(eye);
            double distance = difference.length();
            if (distance < .001 || distance > 24 || !player.hasLineOfSight(candidate)) continue;
            double dot = look.dot(difference.scale(1 / distance));
            if (dot < .72) continue;
            double score = (1 - dot) * 30 + distance * .15;
            if (score < bestScore) { best = candidate; bestScore = score; }
        }
        return best;
    }
    /** Already includes actual held weapon attributes, not the final capped boss HP loss. */
    static float panel(ServerPlayer player) {
        double damage = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        return (float)(Double.isFinite(damage) ? Math.max(.01, Math.min(damage, Float.MAX_VALUE)) : 1.0);
    }

    private DanmakuTargeting() { }
}
