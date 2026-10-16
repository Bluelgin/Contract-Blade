package com.maidweapon.forge.api.ability;

import net.minecraft.resources.ResourceLocation;
import java.util.Map;
import java.util.Set;

public record ContractAbilityMilestone(int level, Set<ResourceLocation> features, Map<ResourceLocation, Double> parameters) {
    public ContractAbilityMilestone {
        if (level < 1 || level > 10) throw new IllegalArgumentException("Milestone level must be 1..10");
        features = Set.copyOf(features); parameters = Map.copyOf(parameters);
        if (parameters.values().stream().anyMatch(value -> !Double.isFinite(value))) throw new IllegalArgumentException("Non-finite ability parameter");
    }
}
