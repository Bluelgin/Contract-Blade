package com.maidweapon.forge.system.akatsuki;

import static org.junit.jupiter.api.Assertions.*;

import com.maidweapon.forge.client.AkatsukiRainDirector;
import java.util.*;
import org.junit.jupiter.api.Test;

class RainDirectorTest {
  @Test
  void independentArrivalsHaveGapsClustersAndNoFourteenTickSheet() {
    var rain = new AkatsukiRainDirector(831);
    rain.begin(100, 48, false, 1, 1);
    var drops = new ArrayList<AkatsukiRainDirector.Drop>();
    var counts = new HashSet<Integer>();
    for (int tick = 100; tick < 150; tick++) {
      var next = rain.poll(tick, 0, 0);
      assertTrue(next.size() <= 4);
      counts.add(next.size());
      drops.addAll(next);
      assertTrue(rain.poll(tick, 0, 0).isEmpty());
    }
    assertEquals(33, drops.size());
    assertTrue(counts.containsAll(List.of(0, 1, 2)));
    assertTrue(drops.get(drops.size() - 1).born() - drops.get(0).born() > 30);
    assertTrue(drops.stream().map(AkatsukiRainDirector.Drop::born).distinct().count() > 30);
  }

  @Test
  void scatterHasCenterAndOuterCoverageAndIndependentHighSpeedFlights() {
    var rain = new AkatsukiRainDirector(921);
    rain.begin(0, 96, true, 2, 1);
    var drops = new ArrayList<AkatsukiRainDirector.Drop>();
    for (int tick = 0; tick < 41; tick++) drops.addAll(rain.poll(tick, 0, 0));
    assertEquals(192, drops.size());
    assertTrue(drops.stream().anyMatch(d -> Math.hypot(d.x(), d.z()) < .5));
    assertTrue(drops.stream().anyMatch(d -> Math.hypot(d.x(), d.z()) > 3));
    for (var d : drops) {
      assertTrue(Math.hypot(d.x(), d.z()) <= 3.6);
      assertTrue(d.height() >= 8 && d.height() <= 12);
      assertTrue(d.reveal() > 0 && d.reveal() < 1);
      assertTrue(d.flight() >= 1.8 && d.flight() <= 3);
      assertEquals(
          .5,
          AkatsukiRainDirector.progress(d.reveal() + d.flight() / 2, d.reveal(), d.flight()),
          1e-10); // Starts fast; no slow quadratic launch.
      assertEquals(1, AkatsukiRainDirector.progress(4, d.reveal(), d.flight()));
    }
    assertTrue(drops.stream().map(AkatsukiRainDirector.Drop::flight).distinct().count() > 180);
  }

  @Test
  void rampageRaisesArrivalDensityAndQualityOnlyScalesCosmetics() {
    int[] totals = new int[3];
    for (int quality = 0; quality <= 2; quality++) {
      var normal = new AkatsukiRainDirector(52);
      var rampage = new AkatsukiRainDirector(52);
      normal.begin(0, 48, false, quality, 1);
      rampage.begin(0, 48, true, quality, 1);
      int n = 0, r = 0;
      for (int tick = 0; tick < 50; tick++) {
        n += normal.poll(tick, 0, 0).size();
        r += rampage.poll(tick, 0, 0).size();
      }
      assertTrue(r >= n * 2);
      totals[quality] = r;
    }
    assertTrue(totals[0] < totals[1] && totals[1] < totals[2]);
  }

  @Test
  void movementLeadIsMinorityBoundedAndLockedAtBirth() {
    var still = new AkatsukiRainDirector(68);
    var moving = new AkatsukiRainDirector(68);
    still.begin(0, 48, true, 1, 1);
    moving.begin(0, 48, true, 1, 1);
    int led = 0, total = 0;
    for (int tick = 0; tick < 45; tick++) {
      var a = still.poll(tick, 0, 0);
      var b = moving.poll(tick, 100, 0);
      assertEquals(a.size(), b.size());
      for (int i = 0; i < a.size(); i++) {
        double shift = b.get(i).x() - a.get(i).x();
        assertTrue(Math.abs(shift) < 1e-10 || Math.abs(shift - 1.4) < 1e-10);
        if (shift > 1) led++;
        total++;
      }
    }
    assertTrue(led > 0 && led < total / 2);
  }

  @Test
  void suspensionNewCueAndStalledClientCannotBankOrReplayShower() {
    var rain = new AkatsukiRainDirector(7);
    rain.begin(0, 96, true, 2, 3);
    assertTrue(rain.poll(0, 0, 0).size() <= 6);
    rain.begin(0, 96, true, 2, 3);
    assertTrue(rain.poll(0, 0, 0).isEmpty());
    rain.cancel();
    assertTrue(rain.poll(5, 0, 0).isEmpty());
    rain.begin(10, 96, true, 2, 3);
    assertTrue(rain.poll(100, 0, 0).isEmpty());
  }
}
