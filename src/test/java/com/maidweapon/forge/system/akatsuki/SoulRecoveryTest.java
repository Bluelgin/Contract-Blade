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
  void activeResonanceSharesDamageBudgetsWithoutStealingNativeCutCooldown() {
    var ledger = new SoulDamageSafetyBudget();
    var owner = java.util.UUID.randomUUID();
    var victim = java.util.UUID.randomUUID();
    var bounds = new SoulDamageSafetyBudget.Limits(128, 512, 384, 3072, 2048, 4, 60);
    assertEquals(50, ledger.reserveResonance(owner, victim, 100, 50, bounds));
    assertEquals(0, ledger.reserveResonance(owner, victim, 101, 50, bounds));
    assertEquals(0, ledger.reserve(owner, victim, 102, SoulDamagePolicy.Mode.SPACE_CUT, 100, bounds));
    assertEquals(100, ledger.reserve(owner, victim, 107, SoulDamagePolicy.Mode.SPACE_CUT, 100, bounds));
    assertEquals(50, ledger.reserveResonance(owner, victim, 110, 50, bounds));
    assertEquals(0, ledger.reserveResonance(owner, victim, 112, Double.NaN, bounds));
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
