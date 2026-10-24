package com.maidweapon.forge.client.tooltip;

import com.maidweapon.forge.system.fox.FoxSpiritState;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "maid_weapon", value = Dist.CLIENT)
public final class FoxSoulTooltipHandler {
  @SubscribeEvent
  public static void tooltip(ItemTooltipEvent event) {
    var stack = event.getItemStack();
    // Infused carriers are composed centrally; never append a second fox summary.
    if (com.maidweapon.forge.item.MaidInfusion.isInfused(stack)) return;
    boolean black =
        FoxSpiritState.BLACK.equals(
            (FoxSpiritState.isSeal(stack)
                    ? FoxSpiritState.sealed(stack)
                    : FoxSpiritState.resident(stack))
                .getString("SpiritId"));
    String prefix = black ? "maid_weapon.fox.black." : "maid_weapon.fox.";
    boolean expanded = net.minecraft.client.gui.screens.Screen.hasShiftDown();
    var lines = event.getToolTip();
    if (FoxSpiritState.isSeal(stack)) {
      if (!event.getToolTip().isEmpty())
        event.getToolTip().set(0, Component.translatable(prefix + "seal.name"));
      SimpleTooltipPresentation.append(lines, expanded,
          "maid_weapon.tooltip.fox.seal." + (black ? "black" : "white") + ".lore",
          details -> SimpleTooltipPresentation.line(details, prefix + "tooltip.seal"));
    } else if (FoxSpiritState.hasResident(stack)) {
      SimpleTooltipPresentation.append(lines, expanded,
          "maid_weapon.tooltip.lore." + (black ? "black" : "white"), details -> {
        SimpleTooltipPresentation.line(details, prefix + "tooltip.resident");
        SimpleTooltipPresentation.line(details, FoxSpiritState.isOriginalHome(stack)
            ? prefix + "tooltip.original" : prefix + "tooltip.away");
        if (!black && FoxSpiritState.isOriginalHome(stack))
          SimpleTooltipPresentation.line(details, "maid_weapon.white_blessing.tooltip");
        if (stack.hasTag() && stack.getTag().contains("MaidWeaponBlackFoxSwordArt"))
          SimpleTooltipPresentation.line(details, "maid_weapon.fox.sa.tooltip");
      });
    } else if (stack.hasTag()
        && stack.getTag().getBoolean(FoxSpiritState.OFFERING)
        && stack.getTag().getBoolean(FoxSpiritState.VACANT)) {
      event.getToolTip().add(Component.translatable("maid_weapon.fox.tooltip.vacant"));
    } else if (stack.hasTag() && stack.getTag().contains("MaidWeaponBlackFoxSwordArt")) {
      SimpleTooltipPresentation.append(lines, expanded, "maid_weapon.tooltip.lore.black",
          details -> SimpleTooltipPresentation.line(details, "maid_weapon.fox.sa.tooltip"));
    }
  }

  private FoxSoulTooltipHandler() {}
}
