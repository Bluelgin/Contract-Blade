package com.maidweapon.forge.system.interior;

import com.maidweapon.forge.system.contract.ContractCarrierData;
import com.maidweapon.forge.system.deployment.ContractTransferSafetyService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Finds the real interior carrier in loaded inventory/menu slots; never creates a recovery copy. */
public final class ContractInteriorCarrierLocator {
    public static ItemStack findContractByBinding(ServerPlayer player, String bindingId) {
        ItemStack carriedByPlayer = findPlayerContractByBinding(player, bindingId);
        if (!carriedByPlayer.isEmpty()) return carriedByPlayer;
        if (bindingId == null || bindingId.isEmpty()) return ItemStack.EMPTY;

        // The active contract may be on the cursor or in an open/modded container.
        // Resolve that real stack instead of creating a recovery copy.
        ItemStack carried = player.containerMenu.getCarried();
        if (!ContractTransferSafetyService.isProjectionPhantom(carried)
                && bindingId.equals(ContractCarrierData.getBindingId(carried))) return carried;
        for (net.minecraft.world.inventory.Slot slot : player.containerMenu.slots) {
            ItemStack stack = slot.getItem();
            if (!ContractTransferSafetyService.isProjectionPhantom(stack)
                    && bindingId.equals(ContractCarrierData.getBindingId(stack))) return stack;
        }
        return ItemStack.EMPTY;
    }

    public static ItemStack findPlayerContractByBinding(ServerPlayer player, String bindingId) {
        if (bindingId == null || bindingId.isEmpty()) return ItemStack.EMPTY;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!ContractTransferSafetyService.isProjectionPhantom(stack)
                    && bindingId.equals(ContractCarrierData.getBindingId(stack))) return stack;
        }
        ItemStack offhand = player.getOffhandItem();
        if (!ContractTransferSafetyService.isProjectionPhantom(offhand)
                && bindingId.equals(ContractCarrierData.getBindingId(offhand))) return offhand;
        return ItemStack.EMPTY;
    }


    private ContractInteriorCarrierLocator() {}
}
