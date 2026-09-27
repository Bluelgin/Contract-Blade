package com.maidweapon.forge.system.contract;

import com.maidweapon.forge.compat.TouhouLittleMaidCompat;
import com.maidweapon.forge.item.MaidWeaponItem;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Single gameplay authority for player <-> contract-maid interactions.
 *
 * <p>Input handlers decide which gesture owns the interaction; this service
 * owns permission checks and capture/recall behavior.</p>
 */
public final class ContractInteractionService {
    public static InteractionResult capture(Player player, Entity maid, ItemStack weapon) {
        if (player == null || maid == null || weapon.isEmpty()) return InteractionResult.PASS;
        if (!TouhouLittleMaidCompat.isTouhouLittleMaidLoaded()
                || !TouhouLittleMaidCompat.isMaidEntity(maid)) {
            return InteractionResult.PASS;
        }
        if (MaidWeaponItem.hasMaidData(weapon) && !MaidWeaponItem.isOwner(weapon, player)) {
            player.displayClientMessage(Component.translatable("maid_weapon.message.not_owner"), true);
            return InteractionResult.FAIL;
        }
        return TouhouLittleMaidCompat.convertMaidToWeapon(player, maid, weapon)
                ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    public static InteractionResult recallHeld(Player player, InteractionHand hand) {
        if (player == null || hand == null) return InteractionResult.PASS;
        return TouhouLittleMaidCompat.isTouhouLittleMaidLoaded()
                ? TouhouLittleMaidCompat.onPlayerShiftRightClick(player, hand)
                : InteractionResult.PASS;
    }

    private ContractInteractionService() {}
}
