package com.maidweapon.forge.system.akatsuki;

import com.maidweapon.forge.api.ability.*;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Ten cumulative capabilities, resolved only through global ContractMastery. */
public final class AkatsukiDomainGrowth {
    public static final ResourceLocation ID = id("akatsuki_domain");
    public static final List<String> ROUTE = List.of("garden", "swords", "ring", "tracking", "adaptation",
            "counter", "chain", "cloak", "rain", "complete");
    public static ResourceLocation id(String name) { return ResourceLocation.parse("maid_weapon:" + name); }
    public static ContractAbilityDefinition fallback() {
        var steps = new ArrayList<ContractAbilityMilestone>();
        for (int i = 0; i < ROUTE.size(); i++)
            steps.add(new ContractAbilityMilestone(i + 1, Set.of(id("akatsuki_" + ROUTE.get(i))), Map.of()));
        return new ContractAbilityDefinition(ID, steps);
    }
    public static void register() {
        ContractAbilityRegistry.register(ID, AkatsukiDomainAuthorization::allows,
                ROUTE.stream().map(n -> id("akatsuki_" + n)).collect(java.util.stream.Collectors.toSet()),
                Map.of(), fallback());
    }
    public static ContractAbilityFeatureSet capabilities(net.minecraft.server.level.ServerPlayer player) {
        var result = ContractAbilityProgressionService.query(player, player.getMainHandItem(), ID);
        return result.status() == ContractAbilityProgressionService.Status.READY
                ? result.capabilities() : ContractAbilityFeatureSet.EMPTY;
    }
    public static boolean has(ContractAbilityFeatureSet features, String name) { return features.has(id("akatsuki_" + name)); }
    private AkatsukiDomainGrowth() {}
}
