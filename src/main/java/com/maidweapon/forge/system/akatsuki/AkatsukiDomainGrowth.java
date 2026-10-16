package com.maidweapon.forge.system.akatsuki;

import com.maidweapon.forge.api.ability.*;
import java.util.*;
import net.minecraft.resources.ResourceLocation;

/** Ten cumulative capabilities, resolved only through global ContractMastery. */
public final class AkatsukiDomainGrowth {
  public static final ResourceLocation ID = id("akatsuki_domain");
  public static final List<String> ROUTE =
      List.of(
          "garden",
          "swords",
          "ring",
          "tracking",
          "adaptation",
          "counter",
          "chain",
          "cloak",
          "rain",
          "complete");

  public static ResourceLocation id(String name) {
    return ResourceLocation.parse("maid_weapon:" + name);
  }

  public static Map<ResourceLocation,Double> parameters(int level){return Map.of(
    id("soul_recovery_rate"),com.maidweapon.forge.system.akatsuki.soul.SoulRecoveryPolicy.cut(level),
    id("soul_aerial_rate"),com.maidweapon.forge.system.akatsuki.soul.SoulRecoveryPolicy.aerial(level),
    id("anchor_lifetime_bonus"),level>=3?10d:0d,id("precision_tolerance"),level>=4?.12:.04,
    id("aerial_hover_ticks"),level>=6?4d:0d,id("debt_lifetime"),level>=9?200d:120d);}
  public static Map<ResourceLocation,ContractAbilityRegistry.Parameter> parameterBounds(){
    var m=new HashMap<ResourceLocation,ContractAbilityRegistry.Parameter>();
    m.put(id("soul_recovery_rate"),new ContractAbilityRegistry.Parameter(0,1));m.put(id("soul_aerial_rate"),new ContractAbilityRegistry.Parameter(0,1));
    m.put(id("anchor_lifetime_bonus"),new ContractAbilityRegistry.Parameter(0,100));m.put(id("precision_tolerance"),new ContractAbilityRegistry.Parameter(0,.25));
    m.put(id("aerial_hover_ticks"),new ContractAbilityRegistry.Parameter(0,8));m.put(id("debt_lifetime"),new ContractAbilityRegistry.Parameter(20,400));return Map.copyOf(m);
  }
  public static double parameter(net.minecraft.server.level.ServerPlayer p,String name){return capabilities(p).parameter(id(name),parameters(com.maidweapon.forge.system.mastery.ContractMasteryService.getLevel(p)).getOrDefault(id(name),0d));}

  public static ContractAbilityDefinition fallback() {
    var steps = new ArrayList<ContractAbilityMilestone>();
    for (int i = 0; i < ROUTE.size(); i++)
      steps.add(
          new ContractAbilityMilestone(i + 1, Set.of(id("akatsuki_" + ROUTE.get(i))), parameters(i+1)));
    return new ContractAbilityDefinition(ID, steps);
  }

  public static void register() {
    ContractAbilityRegistry.register(
        ID,
        AkatsukiDomainAuthorization::allows,
        ROUTE.stream().map(n -> id("akatsuki_" + n)).collect(java.util.stream.Collectors.toSet()),
        parameterBounds(),
        fallback());
  }

  public static ContractAbilityFeatureSet capabilities(
      net.minecraft.server.level.ServerPlayer player) {
    var result = ContractAbilityProgressionService.query(player, player.getMainHandItem(), ID);
    return result.status() == ContractAbilityProgressionService.Status.READY
        ? result.capabilities()
        : ContractAbilityFeatureSet.EMPTY;
  }

  public static boolean has(ContractAbilityFeatureSet features, String name) {
    return features.has(id("akatsuki_" + name));
  }

  private AkatsukiDomainGrowth() {}
}
