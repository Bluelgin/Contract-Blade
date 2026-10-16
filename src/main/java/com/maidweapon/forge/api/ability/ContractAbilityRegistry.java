package com.maidweapon.forge.api.ability;

import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Register in common setup. Reloads replace definitions, never permission providers or feature vocabularies. */
public final class ContractAbilityRegistry {
    public record Parameter(double minimum, double maximum) {
        public Parameter { if (!Double.isFinite(minimum) || !Double.isFinite(maximum) || minimum > maximum) throw new IllegalArgumentException("Parameter range"); }
        public boolean accepts(double value) { return Double.isFinite(value) && value >= minimum && value <= maximum; }
    }
    public record Registration(ContractAbilityProvider provider, Set<ResourceLocation> features,
                               Map<ResourceLocation, Parameter> parameters, ContractAbilityDefinition fallback) {
        public Registration {
            Objects.requireNonNull(provider); features = Set.copyOf(features); parameters = Map.copyOf(parameters);
        }
    }
    private static final Map<ResourceLocation, Registration> REGISTRATIONS = new LinkedHashMap<>();
    private static volatile Map<ResourceLocation, ContractAbilityDefinition> definitions = Map.of();
    public static synchronized void register(ResourceLocation ability, ContractAbilityProvider provider,
            Set<ResourceLocation> features, Map<ResourceLocation, Parameter> parameters, ContractAbilityDefinition fallback) {
        Objects.requireNonNull(ability);
        if (REGISTRATIONS.containsKey(ability)) throw new IllegalArgumentException("Duplicate ability " + ability);
        var registration = new Registration(provider, features, parameters, fallback);
        if (fallback != null) validate(ability, registration, fallback);
        REGISTRATIONS.put(ability, registration);
        if (fallback != null) { var next = new HashMap<>(definitions); next.put(ability, fallback); definitions = Map.copyOf(next); }
    }
    public static synchronized Registration registration(ResourceLocation ability) { return REGISTRATIONS.get(ability); }
    public static ContractAbilityDefinition definition(ResourceLocation ability) { return definitions.get(ability); }
    public static void validate(ResourceLocation ability, Registration registration, ContractAbilityDefinition definition) {
        if (registration == null || !ability.equals(definition.ability())) throw new IllegalArgumentException("Unregistered/mismatched ability " + ability);
        for (var milestone : definition.milestones()) {
            if (!registration.features().containsAll(milestone.features())) throw new IllegalArgumentException("Unregistered feature for " + ability);
            for (var parameter : milestone.parameters().entrySet()) {
                var bounds = registration.parameters().get(parameter.getKey());
                if (bounds == null || !bounds.accepts(parameter.getValue())) throw new IllegalArgumentException("Unregistered/out-of-range parameter " + parameter.getKey());
            }
        }
    }
    public static synchronized void replaceDatapackDefinitions(Map<ResourceLocation, ContractAbilityDefinition> replacements) {
        var next = new HashMap<ResourceLocation, ContractAbilityDefinition>();
        REGISTRATIONS.forEach((ability, registration) -> { if (registration.fallback() != null) next.put(ability, registration.fallback()); });
        replacements.forEach((ability, definition) -> { validate(ability, registration(ability), definition); next.put(ability, definition); });
        definitions = Map.copyOf(next); // Atomic reload; no per-item progress caches to invalidate.
    }
    private ContractAbilityRegistry() { }
}
