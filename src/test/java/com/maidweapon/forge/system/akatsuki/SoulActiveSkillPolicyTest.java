package com.maidweapon.forge.system.akatsuki;

import static org.junit.jupiter.api.Assertions.*;
import com.maidweapon.forge.system.akatsuki.soul.*;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SoulActiveSkillPolicyTest {
  @Test void unknownAndNormalHitsOfferCoresWithoutClaimingDebt() {
    for (String kind : new String[] {"UNVERIFIED_REJECTION", "NORMAL_DEFENSE",
        "CUSTOM_HEALTH_UNKNOWN", "VERIFIED_SPECIAL_CAP", "ABSORPTION"})
      assertTrue(SoulAnchorTriggerPolicy.eligible(true, true, 1, kind));
    assertFalse(SoulAnchorTriggerPolicy.eligible(true, true, 1, "PROTECTED_PHASE"));
    assertFalse(SoulAnchorTriggerPolicy.eligible(true, true, 1, "UNSUPPORTED_SOURCE"));
    assertFalse(SoulAnchorTriggerPolicy.eligible(false, true, 1, "NORMAL_DEFENSE"));
    assertFalse(SoulAnchorTriggerPolicy.eligible(true, false, 1, "NORMAL_DEFENSE"));
    assertFalse(SoulAnchorTriggerPolicy.eligible(true, true, Double.NaN, "NORMAL_DEFENSE"));
    assertFalse(SoulAnchorTriggerPolicy.eligible(true, true, 0, "NORMAL_DEFENSE"));
  }

  @Test void independentDamageGrowsAndFourStagesConserveIt() {
    double previous = 0;
    for (int level = 2; level <= 10; level++) {
      double base = SoulBurstBasePolicy.requested(8, 1000, level, 5, .005);
      assertTrue(base > previous);
      assertEquals(base, Arrays.stream(SoulStageAllocation.split(base, 4)).sum(), .00001);
      previous = base;
    }
    assertEquals(27, SoulBurstBasePolicy.requested(8, 1000, 2, 5, .005), .00001);
    assertEquals(45, previous, .00001);
    assertEquals(0, SoulBurstBasePolicy.requested(8, 1000, 1, 5, .005));
    assertEquals(0, SoulBurstBasePolicy.requested(Double.NaN, 1000, 2, 5, .005));
    assertTrue(Double.isFinite(SoulBurstBasePolicy.requested(Double.MAX_VALUE, 1000, 10, 5, 0)));
  }

  @Test void independentBurstSharesCooldownAcrossPlayersAndWeaponChanges() {
    var scheduler = new SoulHitScheduler();
    UUID target = UUID.randomUUID(), a = UUID.randomUUID(), b = UUID.randomUUID();
    assertEquals(27, scheduler.reserveBurst(a, target, 100, 27, 60));
    assertEquals(0, scheduler.reserveBurst(a, target, 101, 27, 60));
    assertEquals(0, scheduler.reserveBurst(b, target, 101, 27, 60));
    scheduler.finishBurst(target, 112);
    assertEquals(0, scheduler.reserveBurst(b, target, 159, 27, 60));
    assertEquals(27, scheduler.reserveBurst(b, target, 160, 27, 60));
  }
}
