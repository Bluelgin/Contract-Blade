package com.maidweapon.forge.system.akatsuki;

import static com.maidweapon.forge.system.akatsuki.soul.SoulDamagePolicy.Mode.*;
import static org.junit.jupiter.api.Assertions.*;

import com.maidweapon.forge.system.akatsuki.soul.SoulStrikePermissions;
import org.junit.jupiter.api.Test;

class SoulStrikePermissionsTest {
  @Test void highDamageUncreditedCutNeverGainsAntiLimitPermissions() {
    // The server may request 550+ damage at Lv10, but the native Boss cap must
    // remain in charge whenever there is no issued recovery reservation.
    assertFalse(SoulStrikePermissions.overrideEvents(false, true, true));
    assertFalse(SoulStrikePermissions.strong(false, true));
    for (String tag : new String[] {
        "bypasses_invulnerability", "bypasses_cooldown", "bypasses_armor",
        "bypasses_resistance", "bypasses_enchantments", "bypasses_shield",
        "bypasses_effects", "cataclysm:bypasses_hurt_time"
    }) assertFalse(SoulStrikePermissions.bypassTag(SPACE_CUT, false, false, tag), tag);
  }

  @Test void verifiedRecoveryAloneMayCrossSpecialDamageLimits() {
    assertTrue(SoulStrikePermissions.overrideEvents(true, true, true));
    assertTrue(SoulStrikePermissions.strong(true, true));
    assertTrue(SoulStrikePermissions.bypassTag(SPACE_CUT, true, true,
        "bypasses_invulnerability"));
    assertTrue(SoulStrikePermissions.bypassTag(SPACE_CUT, true, true,
        "cataclysm:bypasses_hurt_time"));
    assertFalse(SoulStrikePermissions.overrideEvents(true, false, true));
    assertFalse(SoulStrikePermissions.overrideEvents(true, true, false));
    assertFalse(SoulStrikePermissions.strong(true, false));
    assertFalse(SoulStrikePermissions.bypassTag(SPACE_CUT, true, false,
        "bypasses_invulnerability"));
  }

  @Test void ordinaryCoreCriticalRetainsPostDefenseBonusButNotSpecialBossBypass() {
    assertFalse(SoulStrikePermissions.overrideEvents(false, true, true));
    assertFalse(SoulStrikePermissions.strong(false, true));
    assertTrue(SoulStrikePermissions.bypassTag(SOUL_BURST, false, false,
        "bypasses_armor"));
    assertTrue(SoulStrikePermissions.bypassTag(SOUL_BURST, false, false,
        "bypasses_cooldown"));
    assertFalse(SoulStrikePermissions.bypassTag(SOUL_BURST, false, false,
        "bypasses_invulnerability"));
    assertFalse(SoulStrikePermissions.bypassTag(SOUL_BURST, false, false,
        "cataclysm:bypasses_hurt_time"));
  }
}
