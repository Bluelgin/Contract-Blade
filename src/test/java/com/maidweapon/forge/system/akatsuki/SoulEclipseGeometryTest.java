package com.maidweapon.forge.system.akatsuki;

import static org.junit.jupiter.api.Assertions.*;
import com.maidweapon.forge.system.akatsuki.soul.SoulEclipseGeometry;
import org.junit.jupiter.api.Test;

class SoulEclipseGeometryTest {
  @Test void fixedConstellationHasThreeDistinctSatellitesAndAnInterruptedOrbit() {
    assertEquals(3, SoulEclipseGeometry.STARS);
    assertFalse(SoulEclipseGeometry.onTrack(5.5));
    assertTrue(SoulEclipseGeometry.onTrack(1.2));
    for (int i = 0; i < 3; i++) {
      var p = SoulEclipseGeometry.star(i, 20);
      assertTrue(Double.isFinite(p.x()) && Double.isFinite(p.y()) && Double.isFinite(p.z()));
      for (int j = 0; j < i; j++) {
        var other = SoulEclipseGeometry.star(j, 20);
        assertTrue(Math.hypot(p.x() - other.x(), p.y() - other.y()) > .4);
      }
    }
  }

  @Test void fourStageDefaultBreaksThreeDifferentStarsBeforeMoon() {
    assertEquals(0, SoulEclipseGeometry.brokenStar(0, 4));
    assertEquals(1, SoulEclipseGeometry.brokenStar(1, 4));
    assertEquals(2, SoulEclipseGeometry.brokenStar(2, 4));
    assertEquals(-1, SoulEclipseGeometry.brokenStar(3, 4));
    for (int count : new int[] {3, 5}) {
      for (int i = 0; i < count - 1; i++)
        assertTrue(SoulEclipseGeometry.brokenStar(i, count) >= 0);
      assertEquals(-1, SoulEclipseGeometry.brokenStar(count - 1, count));
    }
  }

  @Test void worldSpaceStarsMatchFixedYawAndRemainSeparateFromTheMoon() {
    var base = new SoulEclipseGeometry.Point(12, 80, -5);
    for (float yaw : new float[] {-180f, -90f, 0f, 45f, 180f}) {
      for (int i = 0; i < SoulEclipseGeometry.STARS; i++) {
        var p = SoulEclipseGeometry.worldStar(base, yaw, i, 200);
        assertTrue(Double.isFinite(p.x()) && Double.isFinite(p.y()) && Double.isFinite(p.z()));
        assertTrue(Math.hypot(p.x() - base.x(), p.y() - base.y() - .85) > .38);
        assertNotEquals(base.x(), p.x(), 1e-4);
      }
    }
    var origin = SoulEclipseGeometry.worldStar(base, 0, 0, 200);
    var rotated = SoulEclipseGeometry.worldStar(base, 90, 0, 200);
    assertEquals(-(origin.z() - base.z()), rotated.x() - base.x(), 1e-7);
    assertEquals(origin.x() - base.x(), rotated.z() - base.z(), 1e-7);
    assertEquals(origin.y(), rotated.y(), 1e-7);
  }

  @Test void animationsExpireAndNeverProduceNonFinitePositions() {
    assertEquals(0, SoulEclipseGeometry.fade(-1, 5));
    assertEquals(0, SoulEclipseGeometry.fade(5, 5));
    assertEquals(1, SoulEclipseGeometry.fade(0, 5));
    assertEquals(0, SoulEclipseGeometry.fade(Double.NaN, 5));
    for (int i = 0; i < 80; i++) {
      var p = SoulEclipseGeometry.orbit(i * Math.PI / 40, i * 3);
      assertTrue(Double.isFinite(p.x()) && Double.isFinite(p.y()) && Double.isFinite(p.z()));
    }
  }
}
