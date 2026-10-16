package com.maidweapon.forge.client.tooltip;

import com.maidweapon.forge.compat.tlm.SoulTalismanService;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "maid_weapon", value = Dist.CLIENT)
public final class ContractTransferTooltip {
    @SubscribeEvent public static void tooltip(ItemTooltipEvent event) {
        var item = event.getItemStack();
        if (item.hasTag() && item.getTag().contains(SoulTalismanService.EMERGENCY))
            event.getToolTip().add(Component.translatable("maid_weapon.transfer.emergency"));
        else if (SoulTalismanService.empty(item) || SoulTalismanService.filled(item))
            event.getToolTip().add(Component.translatable("maid_weapon.transfer.soul"));
        else if (com.maidweapon.forge.compat.tlm.TlmFilmService.isMaidFilm(item)
                && !com.maidweapon.forge.system.fox.FoxSpiritState.isSeal(item))
            event.getToolTip().add(Component.translatable("maid_weapon.transfer.legacy_film"));
    }
    private ContractTransferTooltip() { }
}
