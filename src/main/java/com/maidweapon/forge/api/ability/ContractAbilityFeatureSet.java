package com.maidweapon.forge.api.ability;

import net.minecraft.resources.ResourceLocation;
import java.util.Map;
import java.util.Set;

/** Read-only resolution; contains no executable code, entity references or saved skill state. */
public record ContractAbilityFeatureSet(Set<ResourceLocation> features, Map<ResourceLocation, Double> parameters) {
    public static final ContractAbilityFeatureSet EMPTY = new ContractAbilityFeatureSet(Set.of(), Map.of());
    public ContractAbilityFeatureSet { features = Set.copyOf(features); parameters = Map.copyOf(parameters); }
    public boolean has(ResourceLocation feature) { return features.contains(feature); }
    public double parameter(ResourceLocation id, double fallback) { return parameters.getOrDefault(id, fallback); }
}
