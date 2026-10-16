package com.maidweapon.forge.system.fox;

import com.maidweapon.forge.api.ability.*;
import java.util.*;
import net.minecraft.resources.ResourceLocation;

/** Cumulative player-mastery route. No private blade or spirit levels. */
public final class WhiteFoxBlessingGrowth {
  public static ResourceLocation id(String name) {
    return ResourceLocation.parse("maid_weapon:" + name);
  }

  public static final ResourceLocation ID = id("white_fox_blessing");
  public static final Set<ResourceLocation> FEATURES =
      Set.of(
          id("blessing_guard"),
          id("blessing_cleanse"),
          id("blessing_heal"),
          id("blessing_mobility"),
          id("blessing_lantern"),
          id("blessing_edge"),
          id("blessing_resonance"),
          id("blessing_domain"),
          id("blessing_crisis"),
          id("blessing_oath"));
  public static final Map<ResourceLocation, ContractAbilityRegistry.Parameter> PARAMETERS =
      Map.ofEntries(
          bound("guard_reduction", 0, 0.2),
          bound("guard_cap", 0, 4),
          bound("guard_cooldown", 40, 400),
          bound("cleanse_cooldown", 100, 1200),
          bound("heal_hits", 4, 20),
          bound("heal_interval", 80, 1200),
          bound("heal_amount", 0.1, 2),
          bound("move_speed", 0, 0.15),
          bound("move_ticks", 20, 100),
          bound("guard_shield_ticks", 20, 100),
          bound("edge_damage", 0, 2),
          bound("edge_cooldown", 40, 400),
          bound("resonance_damage", 0, 1),
          bound("ally_radius", 1, 6),
          bound("ally_heal", 0, 1),
          bound("crisis_threshold", 0.1, 0.3),
          bound("crisis_cooldown", 1200, 12000),
          bound("crisis_shield_ticks", 20, 100),
          bound("oath_cooldown", 6000, 72000),
          bound("oath_shield_ticks", 20, 200),
          bound("protection_ticks", 20, 60),
          bound("protection_reduction", 0, 0.25));

  private static Map.Entry<ResourceLocation, ContractAbilityRegistry.Parameter> bound(
      String key, double min, double max) {
    return Map.entry(id(key), new ContractAbilityRegistry.Parameter(min, max));
  }

  private static ContractAbilityMilestone step(
      int level, String feature, Map<String, Double> numbers) {
    var params = new HashMap<ResourceLocation, Double>();
    numbers.forEach((k, v) -> params.put(id(k), v));
    return new ContractAbilityMilestone(level, Set.of(id(feature)), params);
  }

  public static ContractAbilityDefinition fallback() {
    return new ContractAbilityDefinition(
        ID,
        List.of(
            step(
                1,
                "blessing_guard",
                Map.of("guard_reduction", 0.15d, "guard_cap", 3d, "guard_cooldown", 60d)),
            step(2, "blessing_cleanse", Map.of("cleanse_cooldown", 200d)),
            step(
                3,
                "blessing_heal",
                Map.of("heal_hits", 6d, "heal_interval", 100d, "heal_amount", 1d)),
            step(4, "blessing_mobility", Map.of("move_speed", 0.1d, "move_ticks", 40d)),
            step(5, "blessing_lantern", Map.of("guard_shield_ticks", 60d)),
            step(6, "blessing_edge", Map.of("edge_damage", 1.5d, "edge_cooldown", 40d)),
            step(7, "blessing_resonance", Map.of("resonance_damage", 0.75d)),
            step(8, "blessing_domain", Map.of("ally_radius", 5d, "ally_heal", 0.5d)),
            step(
                9,
                "blessing_crisis",
                Map.of(
                    "crisis_threshold",
                    0.25d,
                    "crisis_cooldown",
                    1200d,
                    "crisis_shield_ticks",
                    60d)),
            step(
                10,
                "blessing_oath",
                Map.of(
                    "oath_cooldown",
                    12000d,
                    "oath_shield_ticks",
                    100d,
                    "protection_ticks",
                    40d,
                    "protection_reduction",
                    0.2d))));
  }

  public record Profile(ContractAbilityFeatureSet features) {
    public boolean has(String name) {
      return features.has(id(name));
    }

    public double number(String name) {
      return features.parameter(id(name), 0);
    }

    public long ticks(String name) {
      return (long) number(name);
    }

    public float guardedDamage(float amount) {
      if (!Float.isFinite(amount) || amount <= 0 || !has("blessing_guard")) return amount;
      return (float)
          Math.max(0, amount - Math.min(number("guard_cap"), amount * number("guard_reduction")));
    }
  }

  private WhiteFoxBlessingGrowth() {}
}
