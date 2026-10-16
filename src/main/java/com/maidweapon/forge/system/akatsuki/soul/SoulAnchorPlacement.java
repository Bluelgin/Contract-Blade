package com.maidweapon.forge.system.akatsuki.soul;

import com.maidweapon.common.SoulDamageConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * High, collision-tested anchor: visible above the head, well beyond ordinary
 * ground sweeps. Nearby side offsets are a fallback for roofs/overhangs only.
 */
public final class SoulAnchorPlacement {
  public static Vec3 find(LivingEntity target) {
    double minY = Math.max(
        target.getY() + SoulDamageConfig.ANCHOR_MIN_HEIGHT.get(),
        target.getBoundingBox().maxY + 4.0);
    double highY = Math.max(minY,
        target.getBoundingBox().maxY + SoulDamageConfig.ANCHOR_HEAD_OFFSET.get());
    // Preserve compatibility with the legacy giant-height key while NEVER
    // pushing a tall boss's soul core back into its own hitbox.
    double limitY = Math.max(
        target.getY() + SoulDamageConfig.ANCHOR_MAX_HEIGHT.get(), minY);
    double y = Math.min(highY, limitY);
    if (y < minY) return null;
    double side = Math.max(1.8, target.getBbWidth() * .5 + 1.0);
    double[] offsets = {0, side, side, side, side};
    double[] dx = {0, 1, -1, 0, 0}, dz = {0, 0, 0, 1, -1};
    for (double dy : new double[] {0, -.4, -.8}) {
      if (y + dy < minY) continue;
      for (int i = 0; i < offsets.length; i++) {
        Vec3 point = new Vec3(target.getX() + dx[i] * offsets[i],
            y + dy, target.getZ() + dz[i] * offsets[i]);
        AABB hitbox = new AABB(point.x - .65, point.y, point.z - .65,
            point.x + .65, point.y + 1.8, point.z + .65);
        if (point.y < target.level().getMaxBuildHeight() - 2
            && target.level().hasChunkAt(BlockPos.containing(point))
            && !hitbox.intersects(target.getBoundingBox())
            && !target.level().getBlockCollisions(null, hitbox).iterator().hasNext())
          return point;
      }
    }
    return null;
  }

  private SoulAnchorPlacement() {}
}
