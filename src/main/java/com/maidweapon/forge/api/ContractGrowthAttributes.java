package com.maidweapon.forge.api;

import com.maidweapon.forge.item.MaidInfusion;
import com.maidweapon.forge.system.contract.ContractCarrierData;
import com.maidweapon.forge.system.mastery.ContractMasteryService;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Contextual growth calculation. Skill owners decide where to apply it, once; no attack/SA interception. */
public final class ContractGrowthAttributes {
    public record Values(float masteryBonus, float favorabilityMultiplier) {
        public float additiveBonus() { return Math.max(0, masteryBonus * favorabilityMultiplier); }
        public float dedicatedDamage(float baseDamage) { return (baseDamage + masteryBonus) * favorabilityMultiplier; }
    }
    public static Values resolve(Player player, ItemStack carrier) {
        if (player == null || carrier == null || !MaidInfusion.isInfused(carrier) || !ContractCarrierData.isOwner(carrier, player)
                || ContractCarrierData.isContractSuperseded(carrier)
                || com.maidweapon.forge.system.deployment.ContractTransferSafetyService.isProjectionPhantom(carrier)
                || !player.level().isClientSide && !ContractAuthorization.allows(player, carrier)) return new Values(0, 1);
        ContractMasteryService.observe(player, carrier);
        return new Values(ContractMasteryService.getProgress(player).getAttackDamageBonus(), MaidInfusion.data(carrier).getFavorabilityDamageMultiplier());
    }
    private ContractGrowthAttributes() { }
}
