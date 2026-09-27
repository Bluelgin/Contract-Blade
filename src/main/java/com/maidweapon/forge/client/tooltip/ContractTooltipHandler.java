package com.maidweapon.forge.client.tooltip;

import com.maidweapon.common.MaidWeaponConstants;
import com.maidweapon.forge.item.MaidInfusion;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client-only entry point for every weapon carrying a maid contract. */
@Mod.EventBusSubscriber(modid = MaidWeaponConstants.MOD_ID, value = Dist.CLIENT)
public final class ContractTooltipHandler {
    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (!MaidInfusion.isInfused(stack)) return;

        ContractTooltipComposer.append(
                stack,
                event.getEntity(),
                event.getToolTip()
        );
    }

    private ContractTooltipHandler() {
    }
}
