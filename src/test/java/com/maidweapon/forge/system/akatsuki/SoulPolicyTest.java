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
  void maxMasteryIsUncappedYetCannotBypassSharedCooldownOrRefillRecovery() {
    var b = new SoulDamageSafetyBudget();
    var p = UUID.randomUUID();
    var t = UUID.randomUUID();
    assertEquals(5000, b.reserveUncappedCut(p, t, 0, 5000, limits));
    assertEquals(0, b.reserveUncappedCut(p, t, 1, 5000, limits));
    assertEquals(0, b.reserve(p, t, 4, SPACE_CUT, 128, limits));
    assertEquals(50, b.reserveUncappedCut(p, t, 4, 50, limits));
    assertEquals(0, b.reserveUncappedCut(p, t, 8, Double.NaN, limits));
    assertEquals(100, b.reserveUncappedCut(p, t, 201, 100, limits));
    assertEquals(100, b.reserve(p, t, 201, SOUL_BURST, 100, limits));
  }

  private final SoulDamageSafetyBudget.Limits limits =
      new SoulDamageSafetyBudget.Limits(128, 512, 384, 3072, 2048, 4, 60);

  @Test
  void allModesBladesAndPlayersShareTargetCooldownAndBurstReservation() {
    var b = new SoulDamageSafetyBudget();
    var p = UUID.randomUUID();
    var t = UUID.randomUUID();
    assertEquals(100, b.reserve(p, t, 0, SOUL_BURST, 100, limits));
    assertEquals(0, b.reserve(UUID.randomUUID(), t, 0, SOUL_BURST, 100, limits));
    assertEquals(0, b.reserve(p, t, 1, SPACE_CUT, 10, limits));
    b.finishBurst(t, 10);
    assertEquals(10, b.reserve(p, t, 10, SPACE_CUT, 10, limits));
    assertFalse(b.burstReady(t, 59));
    assertTrue(b.burstReady(t, 60));
    assertEquals(0, b.reserve(p, t, 10, SPACE_CUT, 10, limits));
  }

  @Test
  void failedDamageStaysChargedButPermanentDomainRecoversWithoutBankingCredit() {
    var b = new SoulDamageSafetyBudget();
    var p = UUID.randomUUID();
    var t = UUID.randomUUID();
    assertEquals(384, b.reserve(p, t, 0, SOUL_BURST, 1000, limits));
    b.finishBurst(t, 10);
    assertEquals(0, b.reserve(p, t, 10, SPACE_CUT, 10, limits));
    assertEquals(128, b.reserve(p, t, 20, SPACE_CUT, 1000, limits));
    assertEquals(128, b.reserve(p, t, 100000, SPACE_CUT, 1000, limits));
    assertEquals(0, b.reserve(p, t, 100000, SPACE_CUT, 10, limits));
    assertEquals(0, b.reserve(p, t, 100004, SPACE_CUT, Double.NaN, limits));
  }

  @Test
  void multiplayerCannotMultiplyTheSharedDpsWindow() {
    var b = new SoulDamageSafetyBudget();
    var t = UUID.randomUUID();
    assertEquals(384, b.reserve(UUID.randomUUID(), t, 0, SOUL_BURST, 384, limits));
    b.finishBurst(t, 10);
    assertEquals(128, b.reserve(UUID.randomUUID(), t, 10, SPACE_CUT, 128, limits));
    assertEquals(0, b.reserve(UUID.randomUUID(), t, 14, SPACE_CUT, 128, limits));
    assertEquals(128, b.reserve(UUID.randomUUID(), t, 20, SPACE_CUT, 128, limits));
  }
}
