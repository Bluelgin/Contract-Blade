package com.maidweapon.forge.client.tooltip;

import com.maidweapon.forge.init.ModItems;
import com.maidweapon.forge.item.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.List;
import java.util.function.Consumer;

/** Ordinary items only. Fox spirit and bound-contract presentation have separate owners. */
@Mod.EventBusSubscriber(modid = "maid_weapon", value = Dist.CLIENT)
public final class SimpleItemTooltipHandler {
    @SubscribeEvent public static void tooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        String lore;
        Consumer<List<Component>> details;
        if (stack.getItem() instanceof ContractProjectionBaubleItem bauble) {
            String mode = bauble.mode().name().toLowerCase(java.util.Locale.ROOT);
            lore = bauble.resonanceWaiver() ? "maid_weapon.tooltip.simple.endless_contract.lore"
                    : "maid_weapon.tooltip.projection." + mode + ".flavor";
            details = lines -> {
                SimpleTooltipPresentation.line(lines, "maid_weapon.tooltip.projection.slot");
                SimpleTooltipPresentation.line(lines, "maid_weapon.tooltip.projection." + mode);
                if (bauble.resonanceWaiver()) SimpleTooltipPresentation.line(lines, "maid_weapon.tooltip.endless_contract");
            };
        } else if (stack.getItem() instanceof ResonanceUpgradeItem vein) {
            lore = "maid_weapon.tooltip.simple.vein." + vein.stage() + ".lore";
            details = lines -> {
                SimpleTooltipPresentation.line(lines, "maid_weapon.tooltip.simple.vein.use", vein.stage());
                SimpleTooltipPresentation.line(lines, "maid_weapon.tooltip.vein.global");
            };
        } else if (stack.getItem() instanceof DanmakuTalismanItem) {
            lore = "maid_weapon.tooltip.manka_talisman.lore";
            details = lines -> {
                SimpleTooltipPresentation.line(lines, "maid_weapon.tooltip.simple.manka.equip");
                SimpleTooltipPresentation.line(lines, "maid_weapon.tooltip.manka_talisman.effect");
                SimpleTooltipPresentation.line(lines, "maid_weapon.tooltip.manka_talisman.controls");
                SimpleTooltipPresentation.line(lines, "maid_weapon.tooltip.manka_talisman.restriction");
            };
        } else if (stack.is(ModItems.SPIRIT_CRYSTAL.get())) {
            lore = "maid_weapon.tooltip.simple.crystal.lore";
            details = lines -> {
                SimpleTooltipPresentation.line(lines, "maid_weapon.tooltip.simple.crystal.use");
                SimpleTooltipPresentation.line(lines, "maid_weapon.tooltip.simple.crystal.effect");
            };
        } else if (stack.getItem() instanceof ContractInteriorKeyItem) {
            lore = "maid_weapon.tooltip.simple.key.lore";
            details = lines -> SimpleTooltipPresentation.line(lines, "maid_weapon.tooltip.interior_key.use");
        } else if (stack.is(ModItems.MAID_INJECTOR.get())) {
            lore = "maid_weapon.tooltip.simple.table.lore";
            details = lines -> SimpleTooltipPresentation.line(lines, "maid_weapon.tooltip.simple.table.use");
        } else if (stack.getItem() instanceof PetWeaponItem) {
            lore = stack.is(ModItems.CAT_BLADE.get()) ? "maid_weapon.tooltip.simple.cat.lore" : "maid_weapon.tooltip.simple.dog.lore";
            details = lines -> SimpleTooltipPresentation.line(lines, "maid_weapon.pet.hint");
        } else if (stack.getItem() instanceof MaidWeaponItem && !MaidInfusion.isInfused(stack)) {
            // The legacy development blade is not part of the player-facing redesign.
            if (stack.is(ModItems.TEST_SLASHBLADE.get())) return;
            lore = stack.is(ModItems.MAID_HEAVY.get()) ? "maid_weapon.tooltip.simple.heavy.lore"
                    : stack.is(ModItems.MAID_FAST.get()) ? "maid_weapon.tooltip.simple.fast.lore"
                    : "maid_weapon.tooltip.simple.blade.lore";
            details = lines -> SimpleTooltipPresentation.line(lines, "maid_weapon.tooltip.simple.blade.bind",
                    com.maidweapon.forge.client.ContractCompanionKeys.CALL.getTranslatedKeyMessage());
        } else return;
        SimpleTooltipPresentation.append(event.getToolTip(), Screen.hasShiftDown(), lore, details);
    }

    private SimpleItemTooltipHandler() { }
}
