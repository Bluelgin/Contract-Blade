package com.maidweapon.forge.system.deployment;

import com.maidweapon.forge.compat.SlashBladeCompat;
import com.maidweapon.forge.compat.TouhouLittleMaidHelper;
import com.maidweapon.forge.system.contract.ContractCarrierData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** A rare emergency lookup, not a world-wide carrier index or a per-tick entity scan. */
public record ContractEmergencyCarrier(ItemStack stack, ItemFrame stand) {
    public static ContractEmergencyCarrier resolve(Player owner, Entity maid) {
        ItemStack carried = ContractWeaponLocator.findBoundWeapon(owner, maid.getStringUUID());
        if (!carried.isEmpty()) return new ContractEmergencyCarrier(carried, null);
        String binding = maid.getPersistentData().getString(TouhouLittleMaidHelper.TAG_ENTITY_BINDING_ID);
        if (binding.isEmpty()) return new ContractEmergencyCarrier(ItemStack.EMPTY, null);
        ContractEmergencyCarrier found = null;
        for (ItemFrame frame : maid.level().getEntitiesOfClass(ItemFrame.class, maid.getBoundingBox().inflate(32))) {
            ItemStack blade = frame.getItem();
            if (!SlashBladeCompat.isSlashBlade(blade) || !ContractCarrierData.isOwner(blade, owner)
                    || !binding.equals(ContractCarrierData.getBindingId(blade))
                    || !maid.getStringUUID().equals(ContractCarrierData.getBoundMaidUUID(blade))
                    || ContractCarrierData.isContractSuperseded(blade)) continue;
            if (found != null) return new ContractEmergencyCarrier(ItemStack.EMPTY, null);
            found = new ContractEmergencyCarrier(blade, frame);
        }
        return found == null ? new ContractEmergencyCarrier(ItemStack.EMPTY, null) : found;
    }

    public void publish(Player owner) {
        if (stand != null) stand.setItem(stack);
        else {
            // Recompute crafting/anvil previews from the newly stored input, never copy
            // the input over a refined result (that would erase native blade progress).
            for (var slot : owner.containerMenu.slots) {
                if (slot.getItem() == stack) { slot.setChanged(); break; }
            }
            owner.containerMenu.broadcastChanges();
        }
    }
}
