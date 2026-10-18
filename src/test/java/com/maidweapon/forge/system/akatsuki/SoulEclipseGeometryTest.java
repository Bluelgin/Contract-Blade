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
