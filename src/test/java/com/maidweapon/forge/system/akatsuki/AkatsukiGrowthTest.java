package com.maidweapon.forge.system.akatsuki;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import com.maidweapon.forge.api.ability.*;
import java.io.InputStreamReader;
import java.util.*;
import org.junit.jupiter.api.Test;

class AkatsukiGrowthTest {
  @Test
  void shippedRouteMatchesFallbackAndAddsOneCapabilityAtEveryLevel() throws Exception {
    var fallback = AkatsukiDomainGrowth.fallback();
    try (var stream =
        getClass()
            .getResourceAsStream(
                "/data/maid_weapon/contract_ability_progression/akatsuki_domain.json")) {
      assertNotNull(stream);
      var route = ContractAbilityJson.parse(JsonParser.parseReader(new InputStreamReader(stream)));
      var vocabulary =
          AkatsukiDomainGrowth.ROUTE.stream()
              .map(n -> AkatsukiDomainGrowth.id("akatsuki_" + n))
              .collect(java.util.stream.Collectors.toSet());
      ContractAbilityRegistry.validate(
          AkatsukiDomainGrowth.ID,
          new ContractAbilityRegistry.Registration(
              (p, c) -> false, vocabulary, AkatsukiDomainGrowth.parameterBounds(), fallback),
          route);
      for (int level = 1; level <= 10; level++) {
        assertEquals(fallback.resolve(level), route.resolve(level));
        assertEquals(level, route.resolve(level).features().size());
        for (int n = 0; n < 10; n++)
          assertEquals(
              n < level,
              AkatsukiDomainGrowth.has(route.resolve(level), AkatsukiDomainGrowth.ROUTE.get(n)));
      }
    }
  }
}
