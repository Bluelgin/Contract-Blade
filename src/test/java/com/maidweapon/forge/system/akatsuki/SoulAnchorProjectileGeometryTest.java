package com.maidweapon.forge.system.akatsuki;

import static org.junit.jupiter.api.Assertions.*;

import com.maidweapon.forge.system.akatsuki.soul.SoulAnchorProjectileGeometry;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class SoulAnchorProjectileGeometryTest {
  private static final AABB CORE = new AABB(-.65, 8, -.65, .65, 9.8, .65);

  @Test
  void movingSummonedSwordCanHitHighCoreWithGenerousPadding() {
    assertNotNull(SoulAnchorProjectileGeometry.firstHit(
        CORE, new Vec3(-3, 8.9, 1.1), new Vec3(4, 0, 0)));
    assertNotNull(SoulAnchorProjectileGeometry.firstHit(
        CORE, new Vec3(-3, 7.7, 0), new Vec3(5, 1, 0)));
    assertNull(SoulAnchorProjectileGeometry.firstHit(
        CORE, new Vec3(-3, 8.9, 2.0), new Vec3(5, 0, 0)));
  }

  @Test
  void playerAimMayGuideLockedSummonedSwordButNotAutomaticAim() {
    var eye = new Vec3(-4, 7, 0);
    var look = CORE.getCenter().subtract(eye).normalize();
    assertTrue(SoulAnchorProjectileGeometry.aimedAt(CORE, eye, look));
    assertFalse(SoulAnchorProjectileGeometry.aimedAt(
        CORE, eye, new Vec3(0, 0, -1)));
    assertFalse(SoulAnchorProjectileGeometry.aimedAt(
        CORE, eye, new Vec3(Double.NaN, 0, 0)));
  }

  @Test
  void stationaryTeleportAndInvalidVelocitiesDoNotPass() {
    assertNull(SoulAnchorProjectileGeometry.firstHit(
        CORE, new Vec3(0, 8.9, 0), Vec3.ZERO));
    assertNull(SoulAnchorProjectileGeometry.firstHit(
        CORE, new Vec3(-30, 8.9, 0), new Vec3(35, 0, 0)));
    assertNull(SoulAnchorProjectileGeometry.firstHit(
        CORE, new Vec3(-3, 8.9, 0), new Vec3(Double.NaN, 0, 0)));
  }
}
