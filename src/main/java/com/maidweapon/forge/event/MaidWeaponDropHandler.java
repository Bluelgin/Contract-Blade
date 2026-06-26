package com.maidweapon.forge.event;

import com.maidweapon.common.MaidWeaponConstants;
import com.maidweapon.forge.compat.TouhouLittleMaidCompat;
import com.maidweapon.forge.item.MaidWeaponItem;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 处理潜行+Q释放女仆的逻辑。
 * 潜行时丢弃女仆之刃 → 释放女仆（不丢弃武器本身）。
 */
@Mod.EventBusSubscriber
public class MaidWeaponDropHandler {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onItemToss(ItemTossEvent event) {
        if (!event.getPlayer().isShiftKeyDown()) return;
        if (event.getPlayer().level().isClientSide) return;

        ItemStack dropped = event.getEntity().getItem();
        if (dropped.getTag() != null && dropped.getTag().contains(MaidWeaponConstants.TAG_SLASHBLADE_MODE)
                && MaidWeaponItem.hasMaidData(dropped)
                && MaidWeaponItem.hasMaidEntityData(dropped)) {
            event.setCanceled(true);
            if (TouhouLittleMaidCompat.isTouhouLittleMaidLoaded()) {
                TouhouLittleMaidCompat.onPlayerShiftRightClick(event.getPlayer(), InteractionHand.MAIN_HAND);
            }
        }
    }
}
