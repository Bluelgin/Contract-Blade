package com.maidweapon.forge.event;

import com.maidweapon.common.MaidWeaponConstants;
import com.maidweapon.forge.compat.TouhouLittleMaidCompat;
import com.maidweapon.forge.system.contract.ContractInteractionService;
import com.maidweapon.forge.item.MaidInfusion;
import com.maidweapon.forge.item.MaidWeaponItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 女仆交互事件处理器。
 *
 * 未绑定的专属武器可直接与女仆缔结；SlashBlade 兼容入口保留潜行攻击缔结。
 * 已绑定后的显现/召回全部交给统一 deployment 状态机。
 */
@Mod.EventBusSubscriber
public class MaidInteractionHandler {

    /** Bind an empty dedicated blade before TLM consumes the click to open its GUI. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        ItemStack weapon = event.getItemStack();
        if (event.getHand() != InteractionHand.MAIN_HAND
                || !(weapon.getItem() instanceof MaidWeaponItem)
                || MaidInfusion.isInfused(weapon)
                || !TouhouLittleMaidCompat.isMaidEntity(event.getTarget())) return;

        InteractionResult result = event.getLevel().isClientSide
                ? InteractionResult.SUCCESS
                : ContractInteractionService.capture(event.getEntity(), event.getTarget(), weapon);
        event.setCancellationResult(result);
        event.setCanceled(true);
    }

    /** 检查物品是否为女仆武器（普通版或拔刀剑版） */
    private static boolean isMaidWeapon(ItemStack stack) {
        if (stack.isEmpty()) return false;
        // 普通女仆之刃
        if (stack.getItem() instanceof MaidWeaponItem) return true;
        // 拔刀剑版（通过 SlashBladeMode 标签识别）
        return stack.getTag() != null && stack.getTag().contains(MaidWeaponConstants.TAG_SLASHBLADE_MODE);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttackEntity(AttackEntityEvent event) {
        if (event.getEntity().level().isClientSide()) return;

        ItemStack mainHand = event.getEntity().getMainHandItem();
        if (!isMaidWeapon(mainHand)) return;
        if (!TouhouLittleMaidCompat.isTouhouLittleMaidLoaded()) return;
        if (!TouhouLittleMaidCompat.isMaidEntity(event.getTarget())) return;

        // Once a carrier is already bound, deployment owns manifestation/recall.
        // Cancel attacks against its own maid, but never turn attack input into
        // a second recall path.
        if (MaidInfusion.isInfused(mainHand)) {
            if (MaidWeaponItem.isBoundMaid(mainHand, event.getTarget())) {
                event.setCanceled(true);
            }
            return;
        }

        // Legacy SlashBlade binding gesture still requires sneaking so normal
        // attacks can reach SlashBlade SA logic.
        if (isSlashBladeMode(mainHand) && !event.getEntity().isShiftKeyDown()) return;

        event.setCanceled(true);
        ContractInteractionService.capture(event.getEntity(), event.getTarget(), mainHand);
    }

    private static boolean isSlashBladeMode(ItemStack stack) {
        return stack.getTag() != null && stack.getTag().contains(MaidWeaponConstants.TAG_SLASHBLADE_MODE);
    }
}
