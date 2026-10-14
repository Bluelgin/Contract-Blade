package com.maidweapon.forge.system.contract;

import static org.junit.jupiter.api.Assertions.*;

import com.maidweapon.forge.system.fox.WhiteFoxGuardRuntime;
import org.junit.jupiter.api.Test;

class WhiteFoxGuardRuntimeTest {
  @Test
  void shortCooldownSurvivesHandCyclingButBenefitsStopImmediately() {
    var state = new WhiteFoxGuardRuntime();
    var guard = WhiteFoxGuardRuntime.Action.GUARD;
    assertTrue(state.consume(guard, 100, 60));
    state.move(100, 40);
    state.protect(100, 40);
    assertTrue(state.moving(139));
    assertFalse(state.moving(140));
    state.suspend();
    assertFalse(state.moving(101));
    assertFalse(state.protectedNow(101));
    assertFalse(state.consume(guard, 159, 60));
    assertTrue(state.consume(guard, 160, 60));
    for (var action : WhiteFoxGuardRuntime.Action.values()) {
      state = new WhiteFoxGuardRuntime();
      assertTrue(state.consume(action, 100, 200));
      assertFalse(state.consume(action, 299, 200));
      assertTrue(state.consume(action, 300, 200));
    }
  }

  @Test
  void hitsRequireSixSpacedProofsAndHealingCannotBeSpamOrBankedForever() {
    var state = new WhiteFoxGuardRuntime();
    for (int i = 0; i < 50; i++) assertFalse(state.hit(100, 6, 100, true));
    for (int i = 1; i < 5; i++) assertFalse(state.hit(100 + i * 10, 6, 100, true));
    assertTrue(state.hit(150, 6, 100, true));
    for (int i = 1; i < 10; i++) assertFalse(state.hit(150 + i * 10, 6, 100, true));
    assertTrue(state.hit(250, 6, 100, true));
    for (int i = 0; i < 5; i++) assertFalse(state.hit(400 + i * 10, 6, 100, true));
    assertFalse(state.hit(1000, 6, 100, true));
    state.suspend();
    assertFalse(state.hit(1010, 6, 100, true));
    for (int i = 0; i < 20; i++) assertFalse(state.hit(2000 + i * 10, 6, 100, false));
  }

  @Test
  void offhandOnlyAcceptsEarnedBlackHitsAfterResonanceUnlock() {
    var none = WhiteFoxGuardRuntime.HitMode.NONE;
    var white = WhiteFoxGuardRuntime.HitMode.WHITE;
    var black = WhiteFoxGuardRuntime.HitMode.RESONANCE;
    assertEquals(white, WhiteFoxGuardRuntime.hitMode(true, false, true, false, false));
    assertEquals(none, WhiteFoxGuardRuntime.hitMode(true, false, false, false, true));
    assertEquals(none, WhiteFoxGuardRuntime.hitMode(false, true, false, true, false));
    assertEquals(black, WhiteFoxGuardRuntime.hitMode(false, true, false, true, true));
    assertEquals(none, WhiteFoxGuardRuntime.hitMode(false, true, true, false, true));
    assertEquals(none, WhiteFoxGuardRuntime.hitMode(false, false, true, true, true));
  }
}
