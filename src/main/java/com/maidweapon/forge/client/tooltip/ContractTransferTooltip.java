package com.maidweapon.forge.client.tooltip;

import com.maidweapon.forge.compat.tlm.SoulTalismanService;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "maid_weapon", value = Dist.CLIENT)
public final class ContractTransferTooltip {
    @SubscribeEvent public static void tooltip(ItemTooltipEvent event) {
        var item = event.getItemStack();
        if (com.maidweapon.forge.system.fox.FoxSpiritState.isSeal(item)) return;
        String kind;
        if (item.hasTag() && item.getTag().contains(SoulTalismanService.EMERGENCY)) kind = "emergency";
        else if (SoulTalismanService.empty(item) || SoulTalismanService.filled(item)) kind = "soul";
        else if (com.maidweapon.forge.compat.tlm.TlmFilmService.isMaidFilm(item)
                && !com.maidweapon.forge.system.fox.FoxSpiritState.isSeal(item))
            kind = "legacy_film";
        else return;
        SimpleTooltipPresentation.append(event.getToolTip(), net.minecraft.client.gui.screens.Screen.hasShiftDown(),
                "maid_weapon.tooltip.simple." + kind + ".lore",
                lines -> SimpleTooltipPresentation.line(lines, "maid_weapon.transfer." + kind));
    }
    private ContractTransferTooltip() { }
}
