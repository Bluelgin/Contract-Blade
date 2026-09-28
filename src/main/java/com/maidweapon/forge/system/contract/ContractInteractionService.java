package com.maidweapon.forge.system.contract;

import com.maidweapon.forge.compat.TouhouLittleMaidCompat;
import com.maidweapon.forge.item.MaidInfusion;
import com.maidweapon.forge.item.MaidWeaponItem;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Input-facing contract interaction authority.
 *
 * <p>Gestures arrive here, but every stored/live transition is delegated to
 * {@link ContractLifecycleService}. Dedicated Contract Blade items no longer
 * own a second capture/recall implementation.</p>
 */
public final class ContractInteractionService {
    public static InteractionResult capture(Player player, Entity maid, ItemStack weapon) {
        if (player == null || maid == null || weapon.isEmpty()) return InteractionResult.PASS;
        if (!TouhouLittleMaidCompat.isTouhouLittleMaidLoaded()
                || !TouhouLittleMaidCompat.isMaidEntity(maid)
                || !MaidInfusion.isWeapon(weapon)) {
            return InteractionResult.PASS;
        }
        if (MaidWeaponItem.hasMaidData(weapon) && !MaidWeaponItem.isOwner(weapon, player)) {
            player.displayClientMessage(
                    Component.translatable("maid_weapon.message.not_owner"),
                    true
            );
            return InteractionResult.FAIL;
        }
        return ContractLifecycleService.capture(player, maid, weapon, true)
                ? InteractionResult.SUCCESS
                : InteractionResult.FAIL;
    }

    public static InteractionResult toggleHeld(Player player, InteractionHand hand) {
        if (player == null || hand == null || !TouhouLittleMaidCompat.isTouhouLittleMaidLoaded()) {
            return InteractionResult.PASS;
        }

        ItemStack weapon = player.getItemInHand(hand);
        ContractLifecycleService.ToggleResult result =
                ContractLifecycleService.toggle(player, weapon, true);

        return switch (result) {
            case MANIFESTED, RECALLED -> InteractionResult.SUCCESS;
            case NOT_CONTRACT -> InteractionResult.PASS;
            case NOT_OWNER -> {
                player.displayClientMessage(
                        Component.translatable("maid_weapon.message.not_owner"), true);
                yield InteractionResult.FAIL;
            }
            case SUPERSEDED -> {
                player.displayClientMessage(
                        Component.translatable("maid_weapon.message.superseded_contract"), true);
                yield InteractionResult.FAIL;
            }
            case MAID_NOT_FOUND -> {
                player.displayClientMessage(
                        Component.translatable("maid_weapon.message.manifested_maid_not_found"),
                        true
                );
                yield InteractionResult.FAIL;
            }
            case FAILED -> InteractionResult.FAIL;
        };
    }

    /** @deprecated use {@link #toggleHeld(Player, InteractionHand)}. */
    @Deprecated
    public static InteractionResult recallHeld(Player player, InteractionHand hand) {
        return toggleHeld(player, hand);
    }

    private ContractInteractionService() {}
}
