package com.maidweapon.forge.api.ability;

import com.maidweapon.forge.api.ContractCombatCreditResolver;
import com.maidweapon.forge.system.mastery.ContractMasteryService;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Query only. Unregistered routes leave ordinary skills alone; denied routes never return features. */
public final class ContractAbilityProgressionService {
    public enum Status { UNREGISTERED, DENIED, ENTITLED_WITHOUT_ROUTE, READY }
    public record Result(Status status, int masteryLevel, ContractAbilityFeatureSet capabilities) { }
    public static Result query(ServerPlayer player, ItemStack carrier, ResourceLocation ability) {
        var registration = ContractAbilityRegistry.registration(ability);
        if (registration == null) return new Result(Status.UNREGISTERED, 0, ContractAbilityFeatureSet.EMPTY);
        if (!ContractCombatCreditResolver.valid(new ContractCombatCreditResolver.Credit(player, carrier)))
            return new Result(Status.DENIED, 0, ContractAbilityFeatureSet.EMPTY);
        try {
            if (!registration.provider().entitled(player, carrier)) return new Result(Status.DENIED, 0, ContractAbilityFeatureSet.EMPTY);
        } catch (RuntimeException error) {
            com.maidweapon.forge.compat.CompatDiagnostics.warnOnce("ability-entitlement:" + ability, error);
            return new Result(Status.DENIED, 0, ContractAbilityFeatureSet.EMPTY);
        }
        int mastery = ContractMasteryService.getLevel(player, carrier);
        var definition = ContractAbilityRegistry.definition(ability);
        return definition == null ? new Result(Status.ENTITLED_WITHOUT_ROUTE, mastery, ContractAbilityFeatureSet.EMPTY)
                : new Result(Status.READY, mastery, definition.resolve(mastery));
    }
    private ContractAbilityProgressionService() { }
}
