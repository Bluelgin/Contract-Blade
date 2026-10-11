package com.maidweapon.forge.api.ability;

import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Strict declarative schema. IDs are vocabulary, never reflection/class names or executable expressions. */
public final class ContractAbilityJson {
    public static ContractAbilityDefinition parse(JsonElement input) {
        var root = input.getAsJsonObject(); only(root, Set.of("schema", "ability", "level_source", "milestones"));
        if (integer(root.get("schema")) != 1 || !"player_mastery".equals(root.get("level_source").getAsString()))
            throw new IllegalArgumentException("Unsupported ability schema/level source");
        var ability = id(root.get("ability").getAsString()); var milestones = new ArrayList<ContractAbilityMilestone>();
        var nodes = root.getAsJsonArray("milestones");
        if (nodes.size() > 10) throw new IllegalArgumentException("Too many milestones");
        for (var node : nodes) {
            var object = node.getAsJsonObject(); only(object, Set.of("level", "features", "parameters"));
            var features = new HashSet<ResourceLocation>(); var parameters = new HashMap<ResourceLocation, Double>();
            if (object.has("features")) {
                var array = object.getAsJsonArray("features"); if (array.size() > 128) throw new IllegalArgumentException("Too many features");
                for (var feature : array) if (!features.add(id(feature.getAsString()))) throw new IllegalArgumentException("Duplicate feature");
            }
            if (object.has("parameters")) {
                var entries = object.getAsJsonObject("parameters"); if (entries.size() > 128) throw new IllegalArgumentException("Too many parameters");
                entries.entrySet().forEach(entry -> parameters.put(id(entry.getKey()), number(entry.getValue())));
            }
            milestones.add(new ContractAbilityMilestone(integer(object.get("level")), features, parameters));
        }
        return new ContractAbilityDefinition(ability, milestones);
    }
    private static double number(JsonElement value) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException("Expected numeric parameter");
        double result = value.getAsDouble();
        if (!Double.isFinite(result)) throw new IllegalArgumentException("Expected finite parameter");
        return result;
    }
    private static int integer(JsonElement value) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException("Expected integer");
        return value.getAsBigDecimal().intValueExact();
    }
    private static ResourceLocation id(String value) {
        if (!value.contains(":") || value.length() > 128) throw new IllegalArgumentException("Use namespaced IDs");
        var id = ResourceLocation.tryParse(value); if (id == null) throw new IllegalArgumentException("Invalid ID " + value); return id;
    }
    private static void only(JsonObject object, Set<String> allowed) {
        if (!allowed.containsAll(object.keySet())) throw new IllegalArgumentException("Unknown ability JSON field");
    }
    private ContractAbilityJson() { }
}
