package com.maidweapon.forge.system.akatsuki;

import static org.junit.jupiter.api.Assertions.*;
import com.maidweapon.forge.system.akatsuki.soul.SoulDamagePolicy.Mode;
import com.maidweapon.forge.system.akatsuki.soul.SoulStrikePermissions;
import org.junit.jupiter.api.Test;

class UnknownSoulAggressionTest {
  @Test void onlyUnrecognizedModHostilesGainScopedStrongCuts() {
    assertTrue(SoulStrikePermissions.unrecognizedHostile(false, true, "unknown_mod"));
    assertFalse(SoulStrikePermissions.unrecognizedHostile(true, true, "unknown_mod"));
    assertFalse(SoulStrikePermissions.unrecognizedHostile(false, false, "unknown_mod"));
    assertFalse(SoulStrikePermissions.unrecognizedHostile(false, true, "minecraft"));
    assertFalse(SoulStrikePermissions.unrecognizedHostile(false, true, null));
  }

  @Test void unknownPressureIsWeakButStillScalesInHighStatPacks() {
    assertEquals(1, com.maidweapon.forge.system.akatsuki.soul.SoulNormalCutPolicy
        .unknownPressure(3, .03), 1e-6);
    assertEquals(66, com.maidweapon.forge.system.akatsuki.soul.SoulNormalCutPolicy
        .unknownPressure(550, .12), 1e-6);
    assertEquals(6600, com.maidweapon.forge.system.akatsuki.soul.SoulNormalCutPolicy
        .unknownPressure(55000, .12), 1e-6);
    assertEquals(0, com.maidweapon.forge.system.akatsuki.soul.SoulNormalCutPolicy
        .unknownPressure(Double.NaN, .12), 1e-6);
  }

  @Test void aggressionNeverMakesNonRecoveryCoreCriticalIntoBossBypass() {
    assertTrue(SoulStrikePermissions.overrideEvents(false, true, true, true));
    assertTrue(SoulStrikePermissions.strong(false, true, true));
    assertTrue(SoulStrikePermissions.bypassTag(Mode.SPACE_CUT, false, true,
        "bypasses_invulnerability"));
    assertFalse(SoulStrikePermissions.overrideEvents(false, false, true, true));
    assertFalse(SoulStrikePermissions.bypassTag(Mode.SOUL_BURST, false, false,
        "bypasses_invulnerability"));
    assertFalse(SoulStrikePermissions.bypassTag(Mode.SPACE_CUT, false, false,
        "bypasses_invulnerability"));
  }
}
