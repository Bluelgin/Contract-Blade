package com.maidweapon.forge.system.akatsuki.soul;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Small visual core, generous swept hit; no damage, ownership or boss-policy logic. */
public final class SoulAnchorProjectileGeometry {
  public static final double AIM_PADDING = .55;
  public static final double MAX_SWEEP = 9;
  private static final double MIN_SWEEP_SQR = .04;

  public static Vec3 firstHit(AABB core, Vec3 start, Vec3 velocity) {
    if (core == null || !finite(start) || !finite(velocity)
        || velocity.lengthSqr() < MIN_SWEEP_SQR
        || velocity.lengthSqr() > MAX_SWEEP * MAX_SWEEP) return null;
    AABB region = core.inflate(AIM_PADDING, .65, AIM_PADDING);
    // Starting inside the volume counts as an intentional entry only if
    // the projectile is still moving. Stationary circling swords are ignored.
    if (region.contains(start)) return start;
    return region.clip(start, start.add(velocity)).orElse(null);
  }

  /** The owner's deliberate crosshair aim can override native boss lock-on. */
  public static boolean aimedAt(AABB core, Vec3 eye, Vec3 look) {
    if (core == null || !finite(eye) || !finite(look)
        || look.lengthSqr() < .9 || look.lengthSqr() > 1.1) return false;
    AABB guide = core.inflate(.8, .8, .8);
    return guide.contains(eye)
        || guide.clip(eye, eye.add(look.scale(40))).isPresent();
  }

  private static boolean finite(Vec3 v) {
    return v != null && Double.isFinite(v.x) && Double.isFinite(v.y)
        && Double.isFinite(v.z);
  }

  private SoulAnchorProjectileGeometry() {}
}
