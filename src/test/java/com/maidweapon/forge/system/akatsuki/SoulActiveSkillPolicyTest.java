package com.maidweapon.forge.system.akatsuki;

import static org.junit.jupiter.api.Assertions.*;
import com.maidweapon.forge.system.akatsuki.soul.*;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SoulActiveSkillPolicyTest {
  @Test void unknownAndNormalHitsOfferCoresWithoutClaimingDebt() {
    for (String kind : new String[] {"NORMAL_DEFENSE", "VERIFIED_SPECIAL_CAP", "ABSORPTION"})
      assertTrue(SoulAnchorTriggerPolicy.eligible(true, true, 1, kind));
    assertFalse(SoulAnchorTriggerPolicy.eligible(true, true, 1, "PROTECTED_PHASE"));
    assertFalse(SoulAnchorTriggerPolicy.eligible(true, true, 1, "UNVERIFIED_REJECTION"));
    assertFalse(SoulAnchorTriggerPolicy.eligible(true, true, 1, "CUSTOM_HEALTH_UNKNOWN"));
    assertFalse(SoulAnchorTriggerPolicy.eligible(true, true, 1, "UNSUPPORTED_SOURCE"));
    assertFalse(SoulAnchorTriggerPolicy.eligible(false, true, 1, "NORMAL_DEFENSE"));
    assertFalse(SoulAnchorTriggerPolicy.eligible(true, false, 1, "NORMAL_DEFENSE"));
    assertFalse(SoulAnchorTriggerPolicy.eligible(true, true, Double.NaN, "NORMAL_DEFENSE"));
    assertFalse(SoulAnchorTriggerPolicy.eligible(true, true, 0, "NORMAL_DEFENSE"));
  }

  @Test void observedNativeDamageOnlyProducesUpToFiftyPercentCriticalBonus() {
    for (int level = 2; level <= 10; level++) {
      double rate = AkatsukiDomainGrowth.parameters(level)
          .get(AkatsukiDomainGrowth.id("resonance_hit_ratio"));
      double bonus = SoulResonancePolicy.requested(100, rate);
      assertEquals(50, bonus, .00001);
      assertEquals(bonus, Arrays.stream(SoulStageAllocation.split(bonus, 4)).sum(), .00001);
    }
    assertEquals(0, SoulResonancePolicy.requested(0, .5));
    assertEquals(0, SoulResonancePolicy.requested(Double.NaN, .5));
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
