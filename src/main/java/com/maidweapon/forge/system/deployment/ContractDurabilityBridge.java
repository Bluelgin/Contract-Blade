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
                && stack.getCount() <= count
                && com.maidweapon.forge.system.contract.ContractChannelStorage.channels(stack).stream().anyMatch(
                    channel -> channel.contains("MaidData"))
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
        var seen = new java.util.HashSet<String>();
        for (var channel : com.maidweapon.forge.system.contract.ContractChannelStorage.channels(snapshot)) {
            if (!channel.contains("MaidData")) continue;
            String binding = channel.getString("MaidBindingId");
            if (!seen.add(binding)) continue;
            ItemStack view = snapshot.copy();
            com.maidweapon.forge.system.contract.ContractChannelStorage.restoreContract(view, channel);
            if (!ContractCarrierData.isOwner(view, player)) continue;
            // The ordinary guest channel must not inherit the identity of an archived fox spirit.
            var fox = com.maidweapon.forge.system.fox.FoxSpiritState.resident(view);
            if (!com.maidweapon.forge.api.EmbeddedSpiritApi.isSpirit(view, fox.getString("SpiritId")))
                view.getOrCreateTag().remove(com.maidweapon.forge.system.fox.FoxSpiritState.ROOT);
            ContractCarrierLossService.scheduleDestroyed(player, ContractCarrierData.getBoundMaidUUID(view), binding, view);
        }
    }

    private ContractDurabilityBridge() { }
}
