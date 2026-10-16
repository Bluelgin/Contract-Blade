package com.maidweapon.forge.system.akatsuki;

import static org.junit.jupiter.api.Assertions.*;

import com.maidweapon.forge.system.akatsuki.soul.*;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class SoulRecoveryTest {
  @Test
  void tenLevelsShareOneDeficit() {
    for (int level = 1; level <= 10; level++) {
      var d = new SoulDamageDebt(100, 5);
      double cut = d.reserve(SoulRecoveryPolicy.cut(level), 1000, false);
      d.settle(cut, cut, true);
      double air = d.reserve(SoulRecoveryPolicy.aerial(level), 1000, true);
      d.settle(air, air, true);
      assertEquals(
          95 * Math.max(SoulRecoveryPolicy.cut(level), SoulRecoveryPolicy.aerial(level)),
          d.recovered(),
          1e-8);
      assertTrue(5 + d.recovered() <= 100);
      assertEquals(0, d.reserve(1, 1000, true));
    }
  }

  @Test
  void ordinaryDefenseNeverCreatesDeficit() {
    assertEquals(0, new SoulDamageDebt(100, 100).available(1, true));
    assertEquals(0, new SoulDamageDebt(60, 60).available(1, true));
    assertThrows(IllegalArgumentException.class, () -> new SoulDamageDebt(Double.NaN, 0));
    assertThrows(IllegalArgumentException.class, () -> new SoulDamageDebt(5, 10));
  }

  @Test
  void partialAndFailureAreNotReplayed() {
    var d = new SoulDamageDebt(100, 5);
    double n = d.reserve(.12, 100, false);
    d.settle(n, 4, true);
    assertEquals(0, d.available(.12, false));
    assertEquals(24.5, d.available(.30, true), 1e-8);
    double air = d.reserve(.30, 100, true);
    d.settle(air, 0, true);
    assertEquals(0, d.available(.30, true));
    assertEquals(4, d.recovered());
    assertEquals(n + air, d.attempted(), 1e-8);
  }

  @Test
  void abandonmentDoesNotResetPermission() {
    var d = new SoulDamageDebt(100, 5);
    double n = d.reserve(.45, 100, false);
    d.abandon(n);
    assertEquals(0, d.available(.45, false));
    assertEquals(95, d.available(1, true));
    double air = d.reserve(1, 100, true);
    d.settle(air, 0, false);
    assertEquals(0, d.attempted());
    assertEquals(0, d.available(1, true));
  }

  @Test
  void allReservedStagesFitSingleAttemptCapacity() {
    for (int count = 3; count <= 5; count++)
      for (double total : new double[] {128, 384, 512, 1024}) {
        var a = SoulStageAllocation.split(total, count, 128);
        assertEquals(Math.min(total, count * 128), Arrays.stream(a).sum(), 1e-7);
        for (double x : a) assertTrue(x >= 0 && x <= 128 + .000001);
      }
  }

  @Test
  void levelRatesAreFiniteMonotoneAndBounded() {
    double c = 0, a = 0;
    for (int i = 1; i <= 10; i++) {
      double nc = SoulRecoveryPolicy.cut(i), na = SoulRecoveryPolicy.aerial(i);
      assertTrue(nc >= c && nc <= 1 && na >= a && na <= 1);
      c = nc;
      a = na;
    }
    assertEquals(1, a);
  }
}
