package com.maidweapon.forge.system.deployment;

import com.maidweapon.forge.item.MaidInfusion;
import com.maidweapon.forge.system.contract.ContractCarrierData;
import com.maidweapon.forge.system.interior.ContractInteriorService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** Called only at consumption sites inside durability processing, not arbitrary transfers. */
public final class ContractDurabilityBridge {
    public static void consume(ItemStack stack, int count, LivingEntity user) {
        ItemStack snapshot = ItemStack.EMPTY;
        if (user instanceof ServerPlayer player && count > 0 && !stack.isEmpty()
                && stack.getCount() <= count && MaidInfusion.isInfused(stack)
                && ContractCarrierData.isOwner(stack, player)
                && !ContractCarrierData.isContractSuperseded(stack)) {
            snapshot = stack.copy();
        }
        stack.shrink(count);
        if (snapshot.isEmpty() || !stack.isEmpty()) return;
        ServerPlayer player = (ServerPlayer) user;
        if (ContractInteriorService.isInside(player)
                && !ContractInteriorService.isGallerySession(player)
                && ContractInteriorService.recoverDestroyedActiveContract(player, snapshot)) return;
        ContractCarrierLossService.scheduleDestroyed(player,
                ContractCarrierData.getBoundMaidUUID(snapshot),
                ContractCarrierData.getBindingId(snapshot), snapshot);
    }

    private ContractDurabilityBridge() { }
}
