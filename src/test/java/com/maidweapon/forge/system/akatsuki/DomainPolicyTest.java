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
    assertEquals(4, b.reserve(p, target, 100, 220, limit));
    assertEquals(0, b.reserve(UUID.randomUUID(), target, 100, 220, limit));
    assertEquals(0, b.reserve(UUID.randomUUID(), target, 119, 220, limit));
    assertEquals(4, b.reserve(UUID.randomUUID(), target, 120, 220, limit));
  }

  @Test
  void playerDomainAndAbsoluteCapsAreIndependent() {
    var b = new DomainDamageBudget();
    var p = UUID.randomUUID();
    var t = UUID.randomUUID();
    var unbounded = new DomainDamageBudget.Limits(1000, 1000, 1000, 1000);
    assertEquals(4, b.reserve(p, t, 0, 220, unbounded));
    assertEquals(2, b.reserve(p, UUID.randomUUID(), 0, 220, unbounded));
    assertEquals(0, b.reserve(p, UUID.randomUUID(), 0, 220, unbounded));
    assertEquals(4, b.reserve(p, t, 20, 220, unbounded));
    assertEquals(4, b.reserve(p, t, 40, 220, unbounded));
    assertEquals(4, b.reserve(p, t, 60, 220, unbounded));
    assertEquals(4, b.reserve(p, t, 80, 220, unbounded));
    assertEquals(2, b.reserve(p, t, 100, 220, unbounded));
    b.prune(201); // Cannot refund a recent long-window charge.
    assertEquals(0, b.reserve(p, t, 201, 220, unbounded));
    assertEquals(4, b.reserve(p, t, 220, 220, unbounded));
    assertEquals(2, b.reserve(p, UUID.randomUUID(), 220, 220, unbounded));
  }

  @Test
  void slidingWindowHasNoFixedSecondBoundaryExploit() {
    var b = new DomainDamageBudget();
    var t = UUID.randomUUID();
    var p = UUID.randomUUID();
    var limit = new DomainDamageBudget.Limits(.5, 1, 6, 24);
    assertEquals(.5, b.reserve(p, t, 19, 220, limit));
    assertEquals(0, b.reserve(p, t, 20, 220, limit));
    assertEquals(.5, b.reserve(p, t, 29, 220, limit));
    assertEquals(.5, b.reserve(p, t, 39, 220, limit));
  }

  @Test
  void failedProbeIsStillChargedAndPruningDoesNotRefundRecentDamage() {
    var b = new DomainDamageBudget();
    var t = UUID.randomUUID();
    var p = UUID.randomUUID();
    var limit = new DomainDamageBudget.Limits(1, 1, 6, 24);
    assertEquals(1, b.reserve(p, t, 0, 220, limit));
    b.prune(19);
    assertEquals(0, b.reserve(p, t, 19, 220, limit));
    assertEquals(1, b.reserve(p, t, 201, 220, limit));
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
  void automaticDomainHasNoExpiryAndHealthHysteresisIsStable() {
    var s = new AkatsukiDomainState();
    assertTrue(s.update(true, true, .5, 0, .5, .55));
    assertEquals(AkatsukiDomainState.Phase.NORMAL, s.phase());
    assertFalse(s.update(true, true, .5, 999999, .5, .55));
    s.update(true, true, .499, 1000000, .5, .55);
    assertEquals(AkatsukiDomainState.Phase.RAMPAGE, s.phase());
    assertFalse(s.update(true, true, .54, 1000001, .5, .55));
    s.update(true, true, .55, 1000002, .5, .55);
    assertEquals(AkatsukiDomainState.Phase.NORMAL, s.phase());
    s.update(false, true, .25, 1000003, .5, .55);
    assertFalse(s.valid());
    s.update(true, true, .25, 1000004, .5, .55);
    assertTrue(s.rampage());
    s.update(true, false, 0, 1000005, .5, .55);
    assertFalse(s.valid());
  }

  @Test
  void rescueIsIdempotentAndCannotAttackUntilReignitionCompletes() {
    var s = new AkatsukiDomainState();
    assertFalse(s.rescue(0, 12, 4));
    s.update(true, true, .1, 0, .5, .55);
    assertTrue(s.rescue(0, 12, 4));
    assertFalse(s.rescue(1, 12, 4));
    s.update(true, true, .25, 11, .5, .55);
    assertEquals(AkatsukiDomainState.Phase.RESCUE_COLLAPSE, s.phase());
    assertFalse(s.attacking());
    s.update(true, true, .25, 12, .5, .55);
    assertEquals(AkatsukiDomainState.Phase.RESCUE_REIGNITE, s.phase());
    assertFalse(s.attacking());
    s.update(true, true, .25, 16, .5, .55);
    assertTrue(s.attacking());
    assertTrue(s.rampage());
    s.rescue(17, 12, 4);
    s.update(false, true, .25, 18, .5, .55);
    assertEquals(AkatsukiDomainState.Phase.SUSPENDED, s.phase());
  }

  @Test
  void unboundSwapClosesActiveDomainOnceAndNeverStoresNull() {
    var session = new AkatsukiDomainSession();
    var ends = new java.util.concurrent.atomic.AtomicInteger();
    Runnable close = () -> {
      if (session.active()) ends.incrementAndGet();
      session.suspend();
    };
    assertTrue(session.rebind("contract-red-moon", close));
    session.lifecycle.update(true, true, 1, 100, .5, .55);
    assertTrue(session.active());
    var target = UUID.randomUUID();
    assertTrue(session.contact(101, target));
    assertTrue(session.rebind(null, close));
    assertEquals(1, ends.get());
    assertEquals("", session.binding);
    assertFalse(session.active());
    assertNull(session.target);
    assertFalse(session.rebind(null, close));
    assertFalse(session.rebind("", close));
    assertEquals(1, ends.get());

    // Re-equipping must start a fresh lifecycle, without stale contact or extra END.
    assertTrue(session.rebind("contract-red-moon", close));
    assertEquals(1, ends.get());
    assertTrue(session.lifecycle.update(true, true, 1, 103, .5, .55));
    assertTrue(session.active());
    assertTrue(session.contact(103, target));
    assertTrue(session.rebind("another-contract", close));
    assertEquals(2, ends.get());
    assertEquals("another-contract", session.binding);
  }

  @Test
  void staleNullSessionBindingIsSanitizedWithoutCrashing() {
    var session = new AkatsukiDomainSession();
    session.binding = null; // Simulates legacy invalid state.
    var calls = new java.util.concurrent.atomic.AtomicInteger();
    assertTrue(session.rebind(null, () -> {
      calls.incrementAndGet();
      session.suspend();
    }));
    assertEquals("", session.binding);
    assertEquals(1, calls.get());
    assertFalse(session.rebind(null, () -> fail("same binding must be idempotent")));
  }

  @Test
  void handChangesKeepAttackTimersAndAllBudgetsLiveOutsideSessions() {
    var s = new AkatsukiDomainSession();
    assertTrue(s.due("edge", 10, 20));
    s.suspend();
    assertFalse(s.due("edge", 11, 20));
    assertTrue(s.due("edge", 30, 20));
    var target = UUID.randomUUID();
    assertTrue(s.contact(31, target));
    assertFalse(s.contact(31, target));
    assertTrue(s.contact(32, target));
  }
}
