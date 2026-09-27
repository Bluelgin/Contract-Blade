package com.maidweapon.forge.system.contract;

import com.maidweapon.forge.compat.TouhouLittleMaidCompat;
import com.maidweapon.forge.item.MaidInfusion;
import com.maidweapon.forge.item.MaidWeaponItem;
import com.maidweapon.forge.system.deployment.ContractWeaponLocator;
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

    /**
     * Toggles the dedicated contract in the selected hand:
     * stored maid -> manifest, manifested maid -> capture back into the same binding.
     */
    public static InteractionResult toggleHeld(Player player, InteractionHand hand) {
        if (player == null || hand == null || !TouhouLittleMaidCompat.isTouhouLittleMaidLoaded()) {
            return InteractionResult.PASS;
        }

        ItemStack weapon = player.getItemInHand(hand);
        if (!MaidInfusion.isInfused(weapon)) return InteractionResult.PASS;
        if (!MaidWeaponItem.isOwner(weapon, player)) {
            player.displayClientMessage(
                    Component.translatable("maid_weapon.message.not_owner"), true);
            return InteractionResult.FAIL;
        }
        if (MaidWeaponItem.isContractSuperseded(weapon)) {
            player.displayClientMessage(
                    Component.translatable("maid_weapon.message.superseded_contract"), true);
            return InteractionResult.FAIL;
        }

        if (MaidWeaponItem.hasMaidEntityData(weapon)) {
            return TouhouLittleMaidCompat.convertWeaponToMaid(player, weapon)
                    ? InteractionResult.SUCCESS
                    : InteractionResult.PASS;
        }

        String maidId = MaidWeaponItem.getBoundMaidUUID(weapon);
        Entity maid = maidId == null ? null : ContractWeaponLocator.findManifestedMaid(player, maidId);
        if (maid == null) {
            player.displayClientMessage(
                    Component.translatable("maid_weapon.message.manifested_maid_not_found"), true);
            return InteractionResult.FAIL;
        }

        return capture(player, maid, weapon);
    }

    /** @deprecated use {@link #toggleHeld(Player, InteractionHand)}. */
    @Deprecated
    public static InteractionResult recallHeld(Player player, InteractionHand hand) {
        return toggleHeld(player, hand);
    }

    private ContractInteractionService() {}
}
