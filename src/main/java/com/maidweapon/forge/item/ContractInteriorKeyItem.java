package com.maidweapon.forge.item;

import com.maidweapon.forge.system.interior.ContractInteriorService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;


/** Reusable key that enters the contract carried in the opposite hand. */
public final class ContractInteriorKeyItem extends Item {
    public ContractInteriorKeyItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack key = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(key);
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.pass(key);
        }

        boolean success;
        if (ContractInteriorService.isInside(serverPlayer)) {
            success = ContractInteriorService.exit(serverPlayer);
        } else {
            ItemStack contract = ContractInteriorService.contractForKey(serverPlayer, hand);
            success = ContractInteriorService.enter(serverPlayer, contract);
        }
        return success
                ? InteractionResultHolder.success(key)
                : InteractionResultHolder.fail(key);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
