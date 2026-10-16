package com.maidweapon.forge.system.akatsuki.soul;

import com.maidweapon.common.SoulDamageConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.*;

/**
 * Collision-tested top/side placement; a low ceiling never pushes a core into ground sweep range.
 */
public final class SoulAnchorPlacement {
  public static Vec3 find(LivingEntity t) {
    double y =
        Math.max(
            t.getY() + SoulDamageConfig.ANCHOR_MIN_HEIGHT.get(),
            t.getBoundingBox().maxY + SoulDamageConfig.ANCHOR_HEAD_OFFSET.get());
    double max = t.getY() + SoulDamageConfig.ANCHOR_MAX_HEIGHT.get();
    double side =
        y > max ? Math.min(SoulDamageConfig.LINK_RANGE.get() - 4, t.getBbWidth() * .5 + 1.5) : 0;
    y = Math.min(y, max);
    for (double dy : new double[] {0, -.5, -1}) {
      if (y + dy < t.getY() + SoulDamageConfig.ANCHOR_MIN_HEIGHT.get()) continue;
      for (int i = 0; i < 9; i++) {
        double angle = i * Math.PI / 4, radius = i == 0 ? side : Math.max(side, 1.6);
        var point =
            new Vec3(
                t.getX() + Math.cos(angle) * radius, y + dy, t.getZ() + Math.sin(angle) * radius);
        var box =
            new AABB(
                point.x - .65, point.y, point.z - .65, point.x + .65, point.y + 1.8, point.z + .65);
        if (point.y < t.level().getMaxBuildHeight() - 2
            && t.level().hasChunkAt(BlockPos.containing(point))
            && !box.intersects(t.getBoundingBox())
            && !t.level().getBlockCollisions(null, box).iterator().hasNext()) return point;
      }
    }
    return null;
  }

  private SoulAnchorPlacement() {}
}
