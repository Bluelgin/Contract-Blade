package com.maidweapon.forge.system.akatsuki;

import static org.junit.jupiter.api.Assertions.*;

import com.maidweapon.forge.system.akatsuki.soul.*;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class SoulRecoveryTest {
  @Test
  void normalCutWithNoSpecialLimitDebtUsesLevelScaledDamage() {
    var decision = SoulCutDecision.decide(false, false, 0);
    assertEquals(SoulCutDecision.State.NORMAL, decision.state());
    assertEquals(0, decision.recoveryAmount());
    assertEquals(50, SoulNormalCutPolicy.requested(100, .5, 0));
    // A cap-capable boss below its native threshold still has no verified deficit.
    assertEquals(SoulCutDecision.State.NORMAL,
        SoulCutDecision.decide(false, true, 999).state());
  }

  @Test
  void activeResonanceUsesFrequencyOnlyAndDoesNotStealMainAttack() {
    var hits = new SoulHitScheduler();
    var owner = java.util.UUID.randomUUID();
    var victim = java.util.UUID.randomUUID();
    assertEquals(50_000, hits.reserveResonance(owner, victim, 100, 50_000));
    assertEquals(0, hits.reserveResonance(owner, victim, 101, 50_000));
    assertEquals(0, hits.reserveCut(owner, victim, 102, 80_000, 4));
    assertEquals(80_000, hits.reserveCut(owner, victim, 107, 80_000, 4));
    assertEquals(50_000, hits.reserveResonance(owner, victim, 110, 50_000));
    assertEquals(0, hits.reserveResonance(owner, victim, 112, Double.NaN));
  }

  @Test
  void verifiedReceiptRecoversOnlyItsConfirmedRemainingAllowance() {
    var debt = new SoulDamageDebt(100, 5);
    double requested = debt.available(.45, false);
    var decision = SoulCutDecision.decide(true, false, requested);
    assertEquals(SoulCutDecision.State.RECOVERABLE, decision.state());
    assertEquals(42.75, decision.recoveryAmount(), .000001);
    double claim = debt.reserve(.45, 100, false);
    debt.settle(claim, claim, true);
    assertEquals(SoulCutDecision.State.RECOVERY_LOCKED,
        SoulCutDecision.decide(true, false, debt.available(.45, false)).state());
    assertEquals(0, SoulCutDecision.decide(true, false, 0).recoveryAmount());
  }

  @Test
  void coolingDownExhaustedOrTinyDebtCannotFallbackAndExpirationRestoresNormal() {
    assertEquals(SoulCutDecision.State.RECOVERY_LOCKED,
        SoulCutDecision.decide(true, true, 200).state());
    assertEquals(SoulCutDecision.State.RECOVERY_LOCKED,
        SoulCutDecision.decide(true, false, 0).state());
    assertEquals(SoulCutDecision.State.RECOVERY_LOCKED,
        SoulCutDecision.decide(true, false, .009).state());
    assertEquals(SoulCutDecision.State.RECOVERY_LOCKED,
        SoulCutDecision.decide(true, false, Double.NaN).state());
    assertEquals(SoulCutDecision.State.RECOVERABLE,
        SoulCutDecision.decide(true, false, .01).state());
    // After the valid receipt expires, is invalidated, or the binding changes, the
    // authoritative ledger reports no valid debt; the normal branch becomes available.
    assertEquals(SoulCutDecision.State.NORMAL,
        SoulCutDecision.decide(false, false, 0).state());
  }

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
      assertEquals(0, d.reserve(SoulRecoveryPolicy.aerial(level), 1000, true));
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
  void aerialBurstConservesWholeVerifiedDebtAboveFormer512HpCeiling() {
    for (int stages = 3; stages <= 5; stages++)
      for (double total : new double[] {128, 384, 512, 1_024, 10_000, 1_000_000}) {
        var split = SoulStageAllocation.split(total, stages);
        assertEquals(total, Arrays.stream(split).sum(), Math.max(1e-7, total * 1e-10));
        for (int i = 0; i < stages; i++) {
          assertTrue(split[i] >= 0 && split[i] <= total);
          if (i > 0) assertTrue(split[i] >= split[i - 1]);
        }
      }
    var debt = new SoulDamageDebt(10_005, 5);
    var amount = debt.reserve(1, Double.MAX_VALUE, true);
    assertEquals(10_000, amount);
    var stages = SoulStageAllocation.split(amount, 4);
    for (double n : stages) debt.settle(n, n, true);
    assertEquals(10_000, debt.recovered(), 1e-7);
    assertEquals(0, debt.available(1, true));
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
