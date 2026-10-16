package com.maidweapon.forge.api.ability;

import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Higher milestones union features and override explicitly registered bounded numeric parameters. */
public record ContractAbilityDefinition(ResourceLocation ability, List<ContractAbilityMilestone> milestones) {
    public ContractAbilityDefinition {
        Objects.requireNonNull(ability);
        var ordered = new ArrayList<>(milestones); ordered.sort(Comparator.comparingInt(ContractAbilityMilestone::level));
        if (ordered.size() > 10 || ordered.stream().map(ContractAbilityMilestone::level).distinct().count() != ordered.size())
            throw new IllegalArgumentException("Duplicate/excess milestones");
        milestones = List.copyOf(ordered);
    }
    public ContractAbilityFeatureSet resolve(int mastery) {
        var features = new HashSet<ResourceLocation>(); var parameters = new HashMap<ResourceLocation, Double>();
        for (var milestone : milestones) if (milestone.level() <= mastery) {
            features.addAll(milestone.features()); parameters.putAll(milestone.parameters());
        }
        return new ContractAbilityFeatureSet(features, parameters);
    }
}
