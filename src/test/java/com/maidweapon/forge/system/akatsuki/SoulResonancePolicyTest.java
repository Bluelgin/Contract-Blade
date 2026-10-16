package com.maidweapon.forge.system.akatsuki;

import static org.junit.jupiter.api.Assertions.*;
import com.maidweapon.forge.system.akatsuki.soul.SoulResonancePolicy;
import org.junit.jupiter.api.Test;

class SoulResonancePolicyTest {
  @Test
  void scalesOnlyRealNativeDamageNeverTheBlockedTheoreticalPart() {
    assertEquals(2.5, SoulResonancePolicy.requested(5, .5), .00001);
    assertEquals(50, SoulResonancePolicy.requested(100, .5), .00001);
    assertEquals(0, SoulResonancePolicy.requested(0, .5));
    assertEquals(0, SoulResonancePolicy.requested(Double.NaN, .5));
    assertEquals(0, SoulResonancePolicy.requested(100, Double.NaN));
    assertEquals(0, SoulResonancePolicy.requested(100, 1.0));
  }
  @Test
  void tenLevelsRemainMonotoneAndDatapackDefaultsMatch() {
    double previous = 0;
    for (int level = 1; level <= 10; level++) {
      var values = AkatsukiDomainGrowth.parameters(level);
      double ratio = values.get(AkatsukiDomainGrowth.id("resonance_hit_ratio"));
      assertTrue(ratio > previous);
      assertTrue(ratio <= .5);
      assertEquals(ratio, AkatsukiDomainGrowth.fallback()
          .resolve(level).parameters().get(AkatsukiDomainGrowth.id("resonance_hit_ratio")), .00001);
      previous = ratio;
    }
  }
}
