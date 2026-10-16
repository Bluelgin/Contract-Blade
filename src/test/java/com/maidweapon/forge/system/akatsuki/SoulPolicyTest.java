package com.maidweapon.forge.system.akatsuki;

import static com.maidweapon.forge.system.akatsuki.soul.SoulDamagePolicy.Mode.*;
import static org.junit.jupiter.api.Assertions.*;

import com.maidweapon.forge.system.akatsuki.soul.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SoulPolicyTest {
  private SoulDamagePolicy.Inputs input(double cap, double health) {
    return new SoulDamagePolicy.Inputs(8, health, cap, 1, 1.25, 1.75, 5, .005, 128, 512, 1);
  }

  @Test
  void nativeLimitIsABreakthroughReferenceRatherThanACeiling() {
    assertEquals(10, SoulDamagePolicy.requested(SPACE_CUT, input(5, 200)));
    assertEquals(35, SoulDamagePolicy.requested(SPACE_CUT, input(20, 200)));
    assertEquals(10, SoulDamagePolicy.requested(SPACE_CUT, input(0, 200)));
    assertEquals(64, SoulDamagePolicy.requested(SPACE_CUT, input(10000, 200)));
  }

  @Test
  void millionHealthCannotProduceUnboundedBurstAndStagesConserveReservation() {
    double total = SoulDamagePolicy.requested(SOUL_BURST, input(0, 1000000));
    assertEquals(136, total);
    for (int stages = 3; stages <= 5; stages++) {
      double sum = 0;
      for (int i = 0; i < stages; i++) sum += SoulDamagePolicy.stage(total, i, stages);
      assertEquals(total, sum, .00001);
    }
    assertTrue(SoulDamagePolicy.requested(SOUL_BURST, input(0, 20)) < total);
  }

  @Test
  void ordinaryCutsScaleAndMaxMasteryHasNoGameplayHpLimit() {
    assertEquals(.5, SoulNormalCutPolicy.requested(10, .05, 1));
    assertEquals(2, SoulNormalCutPolicy.requested(100, .08, 2));
    assertEquals(64, SoulNormalCutPolicy.requested(1000, .42, 64));
    assertEquals(50, SoulNormalCutPolicy.requested(100, .5, 0));
    assertEquals(5000, SoulNormalCutPolicy.requested(10000, .5, 0));
    assertEquals(0, SoulNormalCutPolicy.requested(Double.NaN, .5, 0));
    assertEquals(0, SoulNormalCutPolicy.requested(100, Double.NaN, 0));
    assertEquals(0, SoulNormalCutPolicy.requested(100, .5, -1));
  }

  @Test
  void multiMillionDamageHasNoNumericBudgetButCooldownsAreEnforced() {
    var hits = new SoulHitScheduler();
    var p = UUID.randomUUID();
    var t = UUID.randomUUID();
    assertEquals(5_000_000, hits.reserveCut(p, t, 0, 5_000_000, 4));
    assertEquals(0, hits.reserveCut(p, t, 1, 5_000_000, 4));
    assertEquals(1_000_000, hits.reserveResonance(p, t, 1, 1_000_000));
    assertEquals(0, hits.reserveCut(p, t, 4, 5_000_000, 4)); // manual hit priority
    assertEquals(5_000_000, hits.reserveCut(p, t, 7, 5_000_000, 4));
    assertEquals(1_000_000, hits.reserveResonance(p, t, 11, 1_000_000));
    assertEquals(0, hits.reserveResonance(p, t, 12, 1_000_000));
    assertEquals(0, hits.reserveCut(p, t, 14, Double.NaN, 4));
    assertEquals(0, hits.reserveCut(p, t, 14, Double.POSITIVE_INFINITY, 4));
  }

  @Test
  void preciseSoulCoreOutranksAutomaticsWithoutAnHpLimit() {
    var hits = new SoulHitScheduler();
    var p = UUID.randomUUID();
    var t = UUID.randomUUID();
    assertEquals(10_000_000, hits.reserveCut(p, t, 0, 10_000_000, 4));
    assertEquals(9_999_995, hits.reserveBurst(p, t, 1, 9_999_995, 60));
    assertEquals(0, hits.reserveCut(p, t, 4, 200, 4));
    assertEquals(0, hits.reserveResonance(p, t, 4, 100));
    assertEquals(0, hits.reserveBurst(p, t, 4, 9_999_995, 60));
    hits.finishBurst(t, 14);
    assertEquals(0, hits.reserveBurst(p, t, 59, 9_999_995, 60));
    assertEquals(9_999_995, hits.reserveBurst(p, t, 61, 9_999_995, 60));
  }

  @Test
  void noHpLimitsDoNotPermitInvalidOrUnsupportedFloatDamage() {
    var hits = new SoulHitScheduler();
    var p = UUID.randomUUID();
    var t = UUID.randomUUID();
    assertEquals(0, hits.reserveResonance(p, t, 10, Double.NaN));
    assertEquals(0, hits.reserveBurst(p, t, 10, Double.POSITIVE_INFINITY, 60));
    assertEquals(0, hits.reserveCut(p, t, 10, Double.POSITIVE_INFINITY, 4));
    assertEquals(0, hits.reserveCut(p, t, 10, (double) Float.MAX_VALUE * 2, 4));
    assertEquals(0, hits.reserveCut(p, t, 10, -1, 4));
    assertEquals(100_000, hits.reserveCut(p, t, 10, 100_000, 4));
    hits.prune(1000);
    assertEquals(100_000, hits.reserveCut(p, t, 1000, 100_000, 4));
  }

}
