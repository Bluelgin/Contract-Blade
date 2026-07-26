package com.maidweapon.forge.event;

import com.maidweapon.common.data.MaidWeaponData;
import com.maidweapon.common.system.LoyaltySystem;
import com.maidweapon.forge.compat.SlashBladeCompat;
import com.maidweapon.forge.item.MaidInfusion;
import com.maidweapon.forge.item.MaidWeaponItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber
public final class MaidInfusionEventHandler {
    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (!MaidInfusion.isInfused(stack)) return;
        if (MaidInfusion.isContractBlade(stack)) {
            if (event.getEntity() != null && !MaidWeaponItem.isOwner(stack, event.getEntity())) {
                event.getToolTip().add(Component.translatable("maid_weapon.tooltip.borrowed_contract_locked")
                        .withStyle(ChatFormatting.RED));
            }
            return;
        }

        MaidWeaponData data = MaidInfusion.data(stack);
        event.getToolTip().add(Component.empty());
        event.getToolTip().add(Component.translatable("maid_weapon.tooltip.infused_title")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        event.getToolTip().add(Component.translatable("maid_weapon.tooltip.maid_name", data.getMaidName()));
        String owner = MaidWeaponItem.getOwnerName(stack);
        if (owner != null) {
            event.getToolTip().add(Component.translatable("maid_weapon.tooltip.owner", owner));
        }
        event.getToolTip().add(Component.translatable("maid_weapon.tooltip.level", data.getLevel()));
        event.getToolTip().add(Component.translatable("maid_weapon.tooltip.favorability",
                data.getFavorability(), LoyaltySystem.getFavorabilityTitle(data.getFavorability())));
        event.getToolTip().add(Component.translatable("maid_weapon.tooltip.favorability_bonus",
                String.format("%.1f", (data.getFavorabilityDamageMultiplier() - 1.0f) * 100.0f)));
        event.getToolTip().add(Component.translatable("maid_weapon.tooltip.resonance",
                data.getResonance(), MaidWeaponData.MAX_RESONANCE));
        if (event.getEntity() != null && !MaidWeaponItem.isOwner(stack, event.getEntity())) {
            event.getToolTip().add(Component.translatable("maid_weapon.tooltip.borrowed_contract_locked")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        event.getToolTip().add(Component.translatable("maid_weapon.tooltip.additive_damage",
                String.format("%.1f", MaidInfusion.getGenericDamageBonus(data))));
        if (MaidWeaponItem.isContractSuperseded(stack)) {
            event.getToolTip().add(Component.translatable("maid_weapon.tooltip.superseded_contract"));
        } else {
            event.getToolTip().add(Component.translatable("maid_weapon.tooltip.auto_deploy"));
            if (SlashBladeCompat.usesTruePowerTask(stack)) {
                event.getToolTip().add(Component.translatable(
                        "maid_weapon.tooltip.true_power_slashblade"));
            }
        }
    }

    private MaidInfusionEventHandler() {}
}
