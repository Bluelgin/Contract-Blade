package com.maidweapon.forge.client.tooltip;

import com.maidweapon.forge.system.fox.FoxSpiritState;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "maid_weapon", value = Dist.CLIENT)
public final class FoxSoulTooltipHandler {
    @SubscribeEvent
    public static void tooltip(ItemTooltipEvent event) {
        var stack = event.getItemStack();
        if (FoxSpiritState.isSeal(stack)) {
            if (!event.getToolTip().isEmpty()) event.getToolTip().set(0,
                    Component.translatable("maid_weapon.fox.seal.name"));
            event.getToolTip().add(Component.translatable("maid_weapon.fox.tooltip.seal"));
        } else if (FoxSpiritState.hasResident(stack)) {
            event.getToolTip().add(Component.translatable("maid_weapon.fox.tooltip.resident"));
            event.getToolTip().add(Component.translatable(FoxSpiritState.isOriginalHome(stack)
                    ? "maid_weapon.fox.tooltip.original" : "maid_weapon.fox.tooltip.away"));
        } else if (stack.hasTag() && stack.getTag().getBoolean(FoxSpiritState.OFFERING)
                && stack.getTag().getBoolean(FoxSpiritState.VACANT)) {
            event.getToolTip().add(Component.translatable("maid_weapon.fox.tooltip.vacant"));
        }
    }

    private FoxSoulTooltipHandler() { }
}
