package com.maidweapon.forge.system.akatsuki;

import static com.maidweapon.forge.system.akatsuki.DomainDamageAdaptation.Channel.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class DomainPolicyTest {
  @Test
  void nativeHitAndDpsCapsAreDifferentQuantities() {
    var ceiling = new DomainDamageBudget.Limits(4, 4, 6, 24);
    var p = DomainDamageBudget.policy(20, 5, .2, .5, 1, ceiling);
    assertEquals(4, p.hit());
    assertEquals(1, p.targetSecond());
    p = DomainDamageBudget.policy(20, 0, .2, .5, 1, ceiling);
    assertEquals(4, p.hit());
    assertEquals(4, p.targetSecond());
    p = DomainDamageBudget.policy(0, 0, .2, .5, 1, ceiling);
    assertEquals(.5, p.hit());
    assertEquals(1, p.targetSecond());
    p = DomainDamageBudget.policy(Double.NaN, Double.POSITIVE_INFINITY, .9, .5, 1, ceiling);
    assertEquals(.5, p.hit());
    assertEquals(1, p.targetSecond());
    assertEquals(2, DomainDamageBudget.policy(20, 0, .1, .5, 1, ceiling).hit());
    assertEquals(3, DomainDamageBudget.policy(20, 0, .15, .5, 1, ceiling).hit());
  }

  @Test
  void allPlayersAndChannelsShareOneTargetWindow() {
    var b = new DomainDamageBudget();
    var target = UUID.randomUUID();
    var p = UUID.randomUUID();
    var limit = new DomainDamageBudget.Limits(4, 4, 6, 24);
    assertEquals(4, b.reserve(p, target, 100, 0, limit));
    assertEquals(0, b.reserve(UUID.randomUUID(), target, 100, 0, limit));
    assertEquals(0, b.reserve(UUID.randomUUID(), target, 119, 0, limit));
    assertEquals(4, b.reserve(UUID.randomUUID(), target, 120, 0, limit));
  }

  @Test
  void playerDomainAndAbsoluteCapsAreIndependent() {
    var b = new DomainDamageBudget();
    var p = UUID.randomUUID();
    var t = UUID.randomUUID();
    var unbounded = new DomainDamageBudget.Limits(1000, 1000, 1000, 1000);
    assertEquals(4, b.reserve(p, t, 0, 0, unbounded));
    assertEquals(2, b.reserve(p, UUID.randomUUID(), 0, 0, unbounded));
    assertEquals(0, b.reserve(p, UUID.randomUUID(), 0, 0, unbounded));
    assertEquals(1, b.reserve(p, t, 20, 23, unbounded));
    assertEquals(0, b.reserve(p, t, 40, 24, unbounded));
  }

  @Test
  void slidingWindowHasNoFixedSecondBoundaryExploit() {
    var b = new DomainDamageBudget();
    var t = UUID.randomUUID();
    var p = UUID.randomUUID();
    var limit = new DomainDamageBudget.Limits(.5, 1, 6, 24);
    assertEquals(.5, b.reserve(p, t, 19, 0, limit));
    assertEquals(0, b.reserve(p, t, 20, 0, limit));
    assertEquals(.5, b.reserve(p, t, 29, 0, limit));
    assertEquals(.5, b.reserve(p, t, 39, 0, limit));
  }

  @Test
  void failedProbeIsStillChargedAndPruningDoesNotRefundRecentDamage() {
    var b = new DomainDamageBudget();
    var t = UUID.randomUUID();
    var p = UUID.randomUUID();
    var limit = new DomainDamageBudget.Limits(1, 1, 6, 24);
    assertEquals(1, b.reserve(p, t, 0, 0, limit));
    b.prune(19);
    assertEquals(0, b.reserve(p, t, 19, 0, limit));
    assertEquals(1, b.reserve(p, t, 201, 0, limit));
    assertThrows(
        IllegalArgumentException.class, () -> new DomainDamageBudget.Limits(Double.NaN, 1, 1, 1));
  }

  @Test
  void usesHealthDeltaAndCachesWorkingChannel() {
    var a = new DomainDamageAdaptation();
    var channels = List.of(SLASH, RAPID, PIERCING);
    assertEquals(SLASH, a.choose(0, "phase1", channels));
    a.feedback(SLASH, 100, 100, 1, true);
    assertEquals(SLASH, a.choose(10, "phase1", channels));
    a.feedback(SLASH, 100, 100, 1, true);
    assertEquals(RAPID, a.choose(20, "phase1", channels));
    a.feedback(RAPID, 100, 99, 1, true);
    assertEquals(RAPID, a.choose(40, "phase1", channels));
    assertEquals(SLASH, a.choose(60, "phase2", channels));
  }

  @Test
  void ambiguousHealthAndPhaseChangeNeverClaimSuccess() {
    var a = new DomainDamageAdaptation();
    a.choose(0, "phase1", List.of(SLASH));
    a.feedback(SLASH, 100, 50, 1, true);
    assertEquals(0, a.score(SLASH));
    a.feedback(SLASH, 100, 99, 1, false);
    assertEquals(0, a.score(SLASH));
    assertNull(a.choose(20, "phase1", List.of(SLASH)));
    assertNull(a.choose(100, "phase1", List.of(SLASH)));
    assertEquals(SLASH, a.choose(120, "phase1", List.of(SLASH)));
  }

  @Test
  void oneChannelAtATimeWithLimitedSwitchingAndExpiry() {
    var a = new DomainDamageAdaptation();
    var all = List.of(SLASH, RAPID, PIERCING, ARCANE, ABSOLUTE);
    for (int i = 0; i < 10; i++) {
      var c = a.choose(i * 10, "phase1", all);
      assertEquals(all.get(i / 2), c);
      a.feedback(c, 100, 100, 1, true);
    }
    assertNull(a.choose(100, "phase1", all));
    assertEquals(SLASH, a.choose(400, "phase1", all));
    assertNull(a.choose(401, "phase1", List.of()));
  }

  @Test
  void noAirRenewalOrHandSwapCooldownReset() {
    var s = new AkatsukiDomainSession();
    var t = UUID.randomUUID();
    assertFalse(s.contact(0, t, 3, 40, 220, 320));
    assertFalse(s.contact(8, t, 3, 40, 220, 320));
    assertTrue(s.contact(16, t, 3, 40, 220, 320));
    s.spent = 12;
    assertFalse(s.contact(24, t, 3, 40, 220, 320));
    assertEquals(236, s.expires);
    assertEquals(12, s.spent);
    s.suspend();
    assertFalse(s.contact(32, t, 3, 40, 220, 320));
    assertFalse(s.active(32));
    assertEquals(336, s.readyAt);
    assertFalse(s.contact(328, t, 3, 40, 220, 320));
    assertFalse(s.contact(336, t, 3, 40, 220, 320));
    assertTrue(s.contact(344, t, 3, 40, 220, 320));
    assertEquals(0, s.spent);
  }
}
