package com.maidweapon.forge.system.fox;

import com.maidweapon.forge.api.ability.*;
import java.util.*;
import net.minecraft.resources.ResourceLocation;

/** Declarative cumulative route. Later parameters replace earlier values. */
public final class WhiteFoxBlessingGrowth {
  public static ResourceLocation id(String name) {
    return ResourceLocation.parse("maid_weapon:" + name);
  }

  public static final ResourceLocation ID = id("white_fox_blessing");
  public static final Set<ResourceLocation> FEATURES =
      Set.of(
          id("blessing_move"),
          id("blessing_fall"),
          id("blessing_attack"),
          id("blessing_guard"),
          id("blessing_heal"),
          id("blessing_knockback"),
          id("blessing_oath"));
  public static final Map<ResourceLocation, ContractAbilityRegistry.Parameter> PARAMETERS =
      Map.ofEntries(
          bound("move_speed", 0, .15),
          bound("fall_reduction", 0, .3),
          bound("attack_speed", 0, .2),
          bound("damage_reduction", 0, .15),
          bound("knockback", 0, .2),
          bound("heal_delay", 200, 2400),
          bound("heal_interval", 40, 400),
          bound("heal_amount", .1, 2),
          bound("oath_cooldown", 6000, 72000),
          bound("oath_shield_ticks", 20, 200));

  private static Map.Entry<ResourceLocation, ContractAbilityRegistry.Parameter> bound(
      String key, double min, double max) {
    return Map.entry(id(key), new ContractAbilityRegistry.Parameter(min, max));
  }

  private static ContractAbilityMilestone step(
      int level, String feature, Map<String, Double> numbers) {
    var params = new HashMap<ResourceLocation, Double>();
    numbers.forEach((k, v) -> params.put(id(k), v));
    return new ContractAbilityMilestone(
        level, feature.isEmpty() ? Set.of() : Set.of(id(feature)), params);
  }

  public static ContractAbilityDefinition fallback() {
    return new ContractAbilityDefinition(
        ID,
        List.of(
            step(1, "blessing_move", Map.of("move_speed", .03)),
            step(2, "blessing_fall", Map.of("fall_reduction", .10)),
            step(3, "blessing_attack", Map.of("attack_speed", .03)),
            step(4, "blessing_guard", Map.of("damage_reduction", .03)),
            step(
                5,
                "blessing_heal",
                Map.of("heal_delay", 200d, "heal_interval", 80d, "heal_amount", 1d)),
            step(6, "", Map.of("move_speed", .05)),
            step(7, "blessing_knockback", Map.of("knockback", .10)),
            step(8, "", Map.of("attack_speed", .06)),
            step(9, "", Map.of("damage_reduction", .06)),
            step(10, "blessing_oath", Map.of("oath_cooldown", 12000d, "oath_shield_ticks", 100d))));
  }

  public record Profile(ContractAbilityFeatureSet features) {
    public boolean has(String name) {
      return features.has(id(name));
    }

    public double number(String name) {
      return features.parameter(id(name), 0);
    }

    public float damage(float amount, boolean fall) {
      if (!Float.isFinite(amount) || amount <= 0) return amount;
      double value = amount;
      if (fall && has("blessing_fall")) value *= 1 - number("fall_reduction");
      if (has("blessing_guard")) value *= 1 - number("damage_reduction");
      return (float) value;
    }
  }

  private WhiteFoxBlessingGrowth() {}
}
