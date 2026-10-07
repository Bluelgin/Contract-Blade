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
        boolean black = FoxSpiritState.BLACK.equals((FoxSpiritState.isSeal(stack)
                ? FoxSpiritState.sealed(stack) : FoxSpiritState.resident(stack)).getString("SpiritId"));
        String prefix = black ? "maid_weapon.fox.black." : "maid_weapon.fox.";
        if (FoxSpiritState.isSeal(stack)) {
            if (!event.getToolTip().isEmpty()) event.getToolTip().set(0,
                    Component.translatable(prefix + "seal.name"));
            event.getToolTip().add(Component.translatable(prefix + "tooltip.seal"));
        } else if (FoxSpiritState.hasResident(stack)) {
            event.getToolTip().add(Component.translatable(prefix + "tooltip.resident"));
            event.getToolTip().add(Component.translatable(FoxSpiritState.isOriginalHome(stack)
                    ? prefix + "tooltip.original" : prefix + "tooltip.away"));
        } else if (stack.hasTag() && stack.getTag().getBoolean(FoxSpiritState.OFFERING)
                && stack.getTag().getBoolean(FoxSpiritState.VACANT)) {
            event.getToolTip().add(Component.translatable("maid_weapon.fox.tooltip.vacant"));
        }
    }

    private FoxSoulTooltipHandler() { }
}
