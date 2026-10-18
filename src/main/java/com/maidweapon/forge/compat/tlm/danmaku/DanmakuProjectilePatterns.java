package com.maidweapon.forge.compat.tlm.danmaku;

import com.maidweapon.forge.system.danmaku.DanmakuForm;
import com.maidweapon.forge.system.danmaku.DanmakuGrowth;
import com.maidweapon.forge.system.danmaku.DanmakuHitBudget;
import com.github.tartaricacid.touhoulittlemaid.entity.projectile.DanmakuColor;
import com.github.tartaricacid.touhoulittlemaid.entity.projectile.DanmakuType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;

/** Server-side, bounded TLM projectile formations. Real collisions gate every payout. */
public final class DanmakuProjectilePatterns {
    public static void auto(ServerPlayer owner, LivingEntity target, int mastery,
                            float damage, boolean consecutive) {
        var profile = DanmakuGrowth.auto(mastery, consecutive);
        int count = profile.count();
        float total = (float) (Math.max(0, damage) * profile.bonusFraction());
        UUID salvo = ledger(owner, target, total / count, count, 1, 120);
        if (salvo == null) return;
        for (int i = 0; i < count; i++) {
            double side = i - (count - 1) / 2.0;
            double shoulderSide = (i % 2 == 0 ? -1 : 1) * (.80 + Math.abs(side) * .055);
            Vec3 start = DanmakuLaunchGeometry.launch(owner, shoulderSide, .60);
            Vec3 forward = direction(owner, start, target, mastery >= 4)
                    .yRot((float) (side * (mastery >= 3 ? .08 : .035)));
            spawn(owner, start, forward, DanmakuColor.LIGHT_BLUE,
                    DanmakuType.PELLET, .82f, salvo,
                    mastery >= 4 ? target : null, mastery >= 4 ? 16 : 0, i * 2, shoulderSide);
        }
    }

    /** Each timed wave is one budget, regardless of bullet count. */
    public static void wave(ServerPlayer owner, DanmakuForm form, LivingEntity target,
                            int mastery, float baseAttack, int index, boolean finale) {
        if (!form.unlocked(mastery) || index < 0 || index > 2) return;
        switch (form) {
            case NEEDLE -> {
                if (index > 1) return;
                int perWaveCount = 6;
                float each = (float) baseAttack * DanmakuGrowth.fraction(form, mastery) / 12.0;
                UUID volley = ledger(owner, target, each, perWaveCount, 1, 120);
                if (volley == null) return;
                for (int i = 0; i < perWaveCount; i++) {
                    double lateral = i - (perWaveCount - 1) / 2.0;
                    double shoulderSide = (i % 2 == 0 ? -1 : 1) * (.80 + Math.abs(lateral) * .04);
                    Vec3 start = DanmakuLaunchGeometry.launch(owner, shoulderSide, .60);
                    Vec3 vector = direction(owner, start, target, true).yRot((float)(lateral * .055));
                    spawn(owner, start, vector, DanmakuColor.CYAN, DanmakuType.PELLET,
                            1.0f, volley, target, 18, i * 2, shoulderSide);
                }
            }
            case FLOWER -> {
                if (index > 1) return;
                int count = DanmakuGrowth.count(form, mastery);
                float each = (float) baseAttack * DanmakuGrowth.fraction(form, mastery) / (count * 2.0);
                UUID volley = ledger(owner, null, each, count, 3, 120);
                if (volley == null) return;
                Vec3 origin = DanmakuLaunchGeometry.launch(owner, index == 0 ? -.8 : .8, .60);
                Vec3 forward = direction(owner, origin, target, mastery >= 9);
                double spread = index == 0 ? .62 : (mastery >= 9 ? .22 : .37);
                for (int i = 0; i < count; i++) {
                    double normalized = count == 1 ? 0 : (i - (count - 1) / 2.0) / ((count - 1) / 2.0);
                    Vec3 vector = forward.yRot((float) (normalized * spread));
                    float speed = index == 0 ? .68f : .90f;
                    if (mastery >= 7 && i % 2 == 0) speed *= .80f;
                    double shoulderSide = (i % 2 == 0 ? -1 : 1) * (.80 + Math.abs(normalized) * .06);
                    Vec3 start = DanmakuLaunchGeometry.launch(owner, shoulderSide, .60);
                    spawn(owner, start,
                            vector, i % 2 == 0 ? DanmakuColor.PINK : DanmakuColor.PURPLE,
                            DanmakuType.BALL, speed, volley,
                            mastery >= 9 ? target : null, mastery >= 9 ? 9 : 0, i * 2, shoulderSide);
                }
            }
            case FANTASY -> {
                if (index > 3) return;
                float each = (float) baseAttack * DanmakuGrowth.fraction(form, mastery) / 32.0;
                if (index == 3 && finale) each *= 1.15f;
                UUID volley = ledger(owner, target, each, 8, 1, 120);
                if (volley == null) return;
                Vec3 center = target == null
                        ? owner.getEyePosition().add(owner.getLookAngle().scale(7))
                        : target.getEyePosition();
                for (int i = 0; i < 8; i++) {
                    double angle = i * Math.PI / 4.0 + index * .34;
                    double radius = index == 0 ? 4.2 : index == 1 ? 3.3 : index == 2 ? 2.6 : 1.9;
                    Vec3 at = center.add(Math.cos(angle) * radius, .25 + index * .2,
                            Math.sin(angle) * radius);
                    ServerLevel level = owner.serverLevel();
                    if (!level.getBlockState(BlockPos.containing(at)).isAir()) {
                        at = shoulder(owner, (i - 3.5) * .21);
                    }
                    Vec3 direction = center.subtract(at).normalize();
                    if (direction.lengthSqr() < .0001) direction = owner.getLookAngle();
                    DanmakuColor color = index == 0 ? DanmakuColor.PINK
                            : index == 1 ? DanmakuColor.CYAN : DanmakuColor.PURPLE;
                    spawn(owner, at, direction, color, DanmakuType.ORBS,
                            .65f + index * .07f, volley, target, 8, i * 2);
                }
            }
            case GUARD -> { /* Guard is maintained by its own server-side defensive session. */ }
        }
    }

    /** One capped retaliatory strike once an actual protection session ends. */
    public static void guardCounter(ServerPlayer owner, LivingEntity target,
                                    int mastery, float baseAttack) {
        if (target == null || !ContractDanmakuProjectile.safeTarget(owner, target)) return;
        float damage = (float) baseAttack * DanmakuGrowth.fraction(DanmakuForm.GUARD, mastery) / 8.0;
        UUID volley = ledger(owner, target, damage, 8, 1, 120);
        if (volley == null) return;
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4;
            double shoulderSide = (i % 2 == 0 ? -1 : 1) * (.80 + (i / 2) * .04);
            Vec3 at = DanmakuLaunchGeometry.launch(owner, shoulderSide, .60);
            spawn(owner, at, target.getEyePosition().subtract(at),
                    DanmakuColor.LIGHT_GREEN, DanmakuType.ORBS, .79f,
                    volley, target, 13, i * 2, shoulderSide);
        }
    }

    private static UUID ledger(ServerPlayer owner, LivingEntity target,
                               float eachShot, int maxHits, int maxTargets, int lifetime) {
        return DanmakuHitBudget.open(owner.getUUID(), target == null ? null : target.getUUID(),
                eachShot, maxHits, maxTargets, owner.level().getGameTime(), lifetime);
    }

    private static Vec3 sideVector(ServerPlayer owner) {
        Vec3 look = owner.getLookAngle();
        Vec3 right = new Vec3(-look.z, 0, look.x);
        return right.lengthSqr() < .001 ? new Vec3(1, 0, 0) : right.normalize();
    }
    private static Vec3 shoulder(ServerPlayer owner, double sideways) {
        return owner.getEyePosition().add(owner.getLookAngle().scale(.30))
                .add(sideVector(owner).scale(sideways)).add(0, -.33, 0);
    }
    private static Vec3 direction(ServerPlayer owner, Vec3 from, LivingEntity target, boolean lead) {
        Vec3 end = target == null ? null : target.getEyePosition();
        if (end != null && lead) end = end.add(target.getDeltaMovement().scale(.75));
        Vec3 vector = end == null ? owner.getLookAngle() : end.subtract(from);
        return vector.lengthSqr() < .00001 ? owner.getLookAngle() : vector.normalize();
    }

    private static void spawn(ServerPlayer owner, Vec3 at, Vec3 movement,
                              DanmakuColor color, DanmakuType type, float speed, UUID volley,
                              LivingEntity target, int seekingTicks, int delay) {
        DanmakuShotQueue.schedule(owner, at, movement, speed, color, type, volley,
                target, seekingTicks, delay);
    }
    private static void spawn(ServerPlayer owner, Vec3 at, Vec3 movement,
                              DanmakuColor color, DanmakuType type, float speed, UUID volley,
                              LivingEntity target, int seekingTicks, int delay, double shoulderSide) {
        DanmakuShotQueue.schedule(owner, at, movement, speed, color, type, volley,
                target, seekingTicks, delay, shoulderSide);
    }
    private DanmakuProjectilePatterns() { }
}
