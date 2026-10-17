package com.maidweapon.forge.system.mastery;

import static org.junit.jupiter.api.Assertions.*;
import com.maidweapon.forge.api.ability.*;
import com.maidweapon.forge.system.fox.*;
import com.maidweapon.forge.system.akatsuki.AkatsukiDomainGrowth;
import java.util.*;
import org.junit.jupiter.api.Test;

class ContractTooltipSnapshotTest {
  @Test
  void akatsukiCoreUnlockAndMasteryHpLimitAreNotConfused() {
    var routes = Map.of("red", AkatsukiDomainGrowth.fallback());
    var first = ContractTooltipSnapshot.project(1, routes).getCompound("red");
    assertTrue(ContractTooltipSnapshot.unlocked(first, "akatsuki_garden"));
    assertFalse(ContractTooltipSnapshot.unlocked(first, "akatsuki_swords"));
    assertEquals(0, ContractTooltipSnapshot.value(first, "soul_aerial_rate"), 1e-9);
    assertEquals(2, ContractTooltipSnapshot.value(first, "space_cut_level_cap"), 1e-9);
    assertEquals(0, ContractTooltipSnapshot.value(first, "resonance_hit_ratio"), 1e-9);
    var second = ContractTooltipSnapshot.project(2, routes).getCompound("red");
    assertTrue(ContractTooltipSnapshot.unlocked(second, "akatsuki_swords"));
    assertTrue(ContractTooltipSnapshot.value(second, "soul_aerial_rate") > 0);
    var seventh = ContractTooltipSnapshot.project(7, routes).getCompound("red");
    assertFalse(ContractTooltipSnapshot.unlocked(seventh, "akatsuki_cloak"));
    var eighth = ContractTooltipSnapshot.project(8, routes).getCompound("red");
    assertTrue(ContractTooltipSnapshot.unlocked(eighth, "akatsuki_cloak"));
    var max = ContractTooltipSnapshot.project(10, routes).getCompound("red");
    assertEquals(0, ContractTooltipSnapshot.value(max, "space_cut_level_cap"), 1e-9);
    assertEquals(1, ContractTooltipSnapshot.value(max, "soul_aerial_rate"), 1e-9);
    assertEquals(.50, ContractTooltipSnapshot.value(max, "resonance_hit_ratio"), 1e-9);
    assertTrue(ContractTooltipSnapshot.unlocked(max, "akatsuki_complete"));
  }

  @Test
  void projectsRealResolvedValuesAndOnlyUnlockedFeatures() {
    var routes = Map.of(
      "black", BlackFoxSwordCoreGrowth.fallback(),
      "white", WhiteFoxBlessingGrowth.fallback(),
      "red", AkatsukiDomainGrowth.fallback()
    );
    var level5 = ContractTooltipSnapshot.project(5, routes);
    assertEquals(5, level5.getInt("level"));
    var black = level5.getCompound("black");
    assertTrue(ContractTooltipSnapshot.unlocked(black, "combo_mode"));
    assertFalse(ContractTooltipSnapshot.unlocked(black, "multi_hunter"));
    assertEquals(.90, ContractTooltipSnapshot.value(black,"weapon_attack_bonus"),1e-8);
    var white = level5.getCompound("white");
    assertEquals(.25, ContractTooltipSnapshot.value(white,"health_bonus"),1e-8);
    assertTrue(ContractTooltipSnapshot.unlocked(white,"blessing_lantern"));
    assertFalse(ContractTooltipSnapshot.unlocked(white,"blessing_crisis"));
    var red = level5.getCompound("red");
    assertEquals(.25, ContractTooltipSnapshot.value(red,"space_cut_multiplier"),1e-8);
  }

  @Test
  void usesDatapackOverridesAndNeverInventsUnavailableRoutes() {
    var custom = new ContractAbilityDefinition(WhiteFoxBlessingGrowth.ID, List.of(
       new ContractAbilityMilestone(1, Set.of(WhiteFoxBlessingGrowth.id("blessing_guard")),
         Map.of(WhiteFoxBlessingGrowth.id("guard_reduction"), .11))
    ));
    var routes = new HashMap<String, ContractAbilityDefinition>();
    routes.put("white", custom);
    routes.put("black", null); // Registry not ready or no valid route.
    var projected = ContractTooltipSnapshot.project(4, routes);
    assertTrue(ContractTooltipSnapshot.unlocked(projected.getCompound("white"),"blessing_guard"));
    assertEquals(.11,ContractTooltipSnapshot.value(projected.getCompound("white"),"guard_reduction"),1e-8);
    assertFalse(projected.getCompound("black").getBoolean("known"));
    assertEquals(1, ContractTooltipSnapshot.value(projected.getCompound("black"),
        "core_damage_multiplier", 1), 1e-8);
    var minimalRed = new ContractAbilityDefinition(AkatsukiDomainGrowth.ID, List.of(
        new ContractAbilityMilestone(1, Set.of(AkatsukiDomainGrowth.id("akatsuki_garden")),
            Map.of())));
    routes.put("red", minimalRed);
    var withRuntimeFallback = ContractTooltipSnapshot.project(4, routes);
    assertEquals(.21, ContractTooltipSnapshot.value(withRuntimeFallback.getCompound("red"),
        "space_cut_multiplier"), 1e-8);
  }
}
