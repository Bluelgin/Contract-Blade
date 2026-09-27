package com.maidweapon.forge.item;

import com.maidweapon.forge.system.interior.ContractInteriorService;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

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
    public void appendHoverText(
            ItemStack stack,
            @Nullable Level level,
            List<Component> tooltip,
            TooltipFlag flag
    ) {
        tooltip.add(Component.translatable("maid_weapon.tooltip.interior_key.use")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("maid_weapon.tooltip.interior_key.progression")
                .withStyle(ChatFormatting.DARK_PURPLE));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
