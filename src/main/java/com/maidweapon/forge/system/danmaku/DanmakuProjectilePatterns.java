package com.maidweapon.forge.system.danmaku;

import com.github.tartaricacid.touhoulittlemaid.entity.projectile.DanmakuColor;
import com.github.tartaricacid.touhoulittlemaid.entity.projectile.DanmakuType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Spawn positions/patterns are server-owned. The per-array damage budget is split
 * among bullets, so level 10 never multiplies full sword damage by projectile count.
 */
public final class DanmakuProjectilePatterns {
    public static void auto(ServerPlayer owner, LivingEntity target, int mastery, float damage) {
        var profile = DanmakuGrowth.auto(mastery);
        double total = Math.max(0, damage) * profile.bonusFraction();
        if (total <= 0) return;
        Vec3 direction = direction(owner, target);
        for (int i = 0; i < profile.count(); i++) {
            double offset = i - (profile.count() - 1) / 2.0;
            spawn(owner, direction.yRot((float) (offset * .065)),
                    (float) (total / profile.count()), DanmakuColor.LIGHT_BLUE, DanmakuType.PELLET, 0.85f);
        }
    }
    public static void cast(ServerPlayer owner, DanmakuForm form, LivingEntity target,
                            int mastery, float baseAttack) {
        if (!form.unlocked(mastery)) return;
        int count = DanmakuGrowth.count(form, mastery);
        float each = (float) (Math.max(0, baseAttack) * DanmakuGrowth.fraction(form, mastery) / count);
        if (each <= 0) return;
        Vec3 forward = direction(owner, target);
        for (int i = 0; i < count; i++) {
            Vec3 vector;
            DanmakuColor color;
            DanmakuType shape;
            switch (form) {
                case NEEDLE -> {
                    vector = forward.yRot((float) ((i - (count-1)/2.0) * .025));
                    color = DanmakuColor.CYAN;
                    shape = DanmakuType.PELLET;
                }
                case FLOWER -> {
                    vector = forward.yRot((float) ((i - (count-1)/2.0) * (Math.PI/3.0) / (count-1)));
                    color = (i % 2 == 0) ? DanmakuColor.PINK : DanmakuColor.PURPLE;
                    shape = DanmakuType.BALL;
                }
                case GUARD -> {
                    vector = new Vec3(Math.cos(i * 2 * Math.PI / count), .08,
                            Math.sin(i * 2 * Math.PI / count));
                    color = DanmakuColor.LIGHT_GREEN;
                    shape = DanmakuType.ORBS;
                }
                case FANTASY -> {
                    int ring = i / 8;
                    double angle = (i % 8) * Math.PI / 4.0 + ring * .17;
                    vector = new Vec3(Math.cos(angle), .15 + ring * .12, Math.sin(angle));
                    color = ring == 0 ? DanmakuColor.PINK : ring == 1 ? DanmakuColor.CYAN : DanmakuColor.PURPLE;
                    shape = DanmakuType.ORBS;
                }
                default -> throw new IllegalStateException();
            }
            spawn(owner, vector, each, color, shape, form == DanmakuForm.FANTASY ? .5f : .8f);
        }
    }
    private static Vec3 direction(ServerPlayer owner, LivingEntity target) {
        Vec3 from = owner.getEyePosition().add(owner.getLookAngle().scale(.4));
        Vec3 to = target == null ? owner.getLookAngle() : target.getEyePosition().subtract(from);
        return to.lengthSqr() < 1.0e-5 ? owner.getLookAngle() : to.normalize();
    }
    private static void spawn(ServerPlayer owner, Vec3 direction, float damage,
                              DanmakuColor color, DanmakuType type, float velocity) {
        if (!(owner.level() instanceof ServerLevel world) || damage <= 0) return;
        var bullet = new ContractDanmakuProjectile(world, owner);
        Vec3 at = owner.getEyePosition().add(owner.getLookAngle().scale(.55));
        bullet.setPos(at.x, at.y, at.z);
        bullet.setColor(color);
        bullet.setDanmakuType(type);
        bullet.setDamage(damage);
        bullet.setGravityVelocity(0);
        bullet.shoot(direction.x, direction.y, direction.z, velocity, 0);
        world.addFreshEntity(bullet);
    }
    private DanmakuProjectilePatterns() { }
}
