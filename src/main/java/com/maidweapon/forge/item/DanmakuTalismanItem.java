package com.maidweapon.forge.item;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** Ordinary-contract maid bauble. Does not store level or gameplay authority in item NBT. */
public final class DanmakuTalismanItem extends Item {
    public DanmakuTalismanItem() { super(new Properties().stacksTo(1).rarity(Rarity.RARE)); }

    @Override public void appendHoverText(ItemStack stack, @Nullable Level level,
                                          List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("maid_weapon.tooltip.manka_talisman.lore")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("maid_weapon.tooltip.manka_talisman.effect")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable("maid_weapon.tooltip.manka_talisman.controls")
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("maid_weapon.tooltip.manka_talisman.restriction")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
