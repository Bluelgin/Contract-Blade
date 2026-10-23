package com.maidweapon.forge.compat.tlm.danmaku;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.level.ClipContext;

/**
 * Launches behind either shoulder, not from the eyes or the weapon tip.
 * Inputs are in player-relative coordinates, so deferred shots can follow
 * the newest owner position and yaw rather than firing from an old location.
 */
public final class DanmakuLaunchGeometry {
    public static Vec3 launch(ServerPlayer owner, double side, double back) {
        Vec3 look = owner.getLookAngle();
        Vec3 horizontal = new Vec3(look.x, 0, look.z);
        if (horizontal.lengthSqr() < .001) horizontal = new Vec3(0, 0, 1);
        horizontal = horizontal.normalize();
        Vec3 right = new Vec3(-horizontal.z, 0, horizontal.x);
        Vec3 desired = owner.position().add(right.scale(side))
                .subtract(horizontal.scale(back)).add(0, 1.40, 0);
        Vec3 from = owner.position().add(0, 1.30, 0);
        // Rear launch inside a wall would vanish instantly. Fall back to the
        // outer shoulder, then a safe near-body position if even that is blocked.
        var hit = owner.level().clip(new ClipContext(from, desired,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
        if (hit.getType() != HitResult.Type.MISS) {
            desired = from.add(right.scale(Math.signum(side) * .55));
            if (owner.level().clip(new ClipContext(from, desired,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner)).getType()
                    != HitResult.Type.MISS) desired = from;
        }
        return desired;
    }
    private DanmakuLaunchGeometry() {}
}
