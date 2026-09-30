package com.maidweapon.forge.event;

import com.maidweapon.common.MaidWeaponConstants;
import com.maidweapon.forge.compat.TouhouLittleMaidCompat;
import com.maidweapon.forge.system.contract.ContractInteractionService;
import com.maidweapon.forge.item.MaidWeaponItem;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 女仆交互事件处理器。
 *
 * 普通模式：左键女仆 → 捕获
 * 拔刀剑模式：潜行+左键女仆 → 捕获，潜行+左键空 → 释放
 */
@Mod.EventBusSubscriber
public class MaidInteractionHandler {

    /** 检查物品是否为女仆武器（普通版或拔刀剑版） */
    private static boolean isMaidWeapon(ItemStack stack) {
        if (stack.isEmpty()) return false;
        // 普通女仆之刃
        if (stack.getItem() instanceof MaidWeaponItem) return true;
        // 拔刀剑版（通过 SlashBladeMode 标签识别）
        return stack.getTag() != null && stack.getTag().contains(MaidWeaponConstants.TAG_SLASHBLADE_MODE);
    }

    /** 检查是否持有已绑定的女仆武器 */
    private static boolean hasBoundMaid(ItemStack stack) {
        return isMaidWeapon(stack) && MaidWeaponItem.hasMaidData(stack);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttackEntity(AttackEntityEvent event) {
        if (event.getEntity().level().isClientSide()) return;

        ItemStack mainHand = event.getEntity().getMainHandItem();
        if (!isMaidWeapon(mainHand)) return;
        if (!TouhouLittleMaidCompat.isTouhouLittleMaidLoaded()) return;
        if (!TouhouLittleMaidCompat.isMaidEntity(event.getTarget())) return;

        // 拔刀剑模式需要潜行才捕获（否则正常攻击触发SA）
        if (isSlashBladeMode(mainHand) && !event.getEntity().isShiftKeyDown()) return;

        event.setCanceled(true);
        ContractInteractionService.capture(event.getEntity(), event.getTarget(), mainHand);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLeftClickEmpty(PlayerInteractEvent.LeftClickEmpty event) {
        ItemStack mainHand = event.getEntity().getMainHandItem();
        if (!isSlashBladeMode(mainHand)) return; // 仅拔刀剑版
        if (!event.getEntity().isShiftKeyDown()) return;
        if (!MaidWeaponItem.hasMaidEntityData(mainHand)) return;

        // 客户端返回 SUCCESS 以触发数据包发送到服务端
        if (event.getLevel().isClientSide()) {
            return; // 客户端侧不做释放操作，仅放行数据包
        }

        ContractInteractionService.toggleHeld(event.getEntity(), InteractionHand.MAIN_HAND);
    }

    private static boolean isSlashBladeMode(ItemStack stack) {
        return stack.getTag() != null && stack.getTag().contains(MaidWeaponConstants.TAG_SLASHBLADE_MODE);
    }
}
