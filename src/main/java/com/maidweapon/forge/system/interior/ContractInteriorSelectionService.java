package com.maidweapon.forge.system.interior;

import com.maidweapon.forge.item.MaidInfusion;
import com.maidweapon.forge.item.MaidWeaponItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** First-entry terrain chooser for one concrete contract interior. */
public final class ContractInteriorSelectionService {
    public static void prompt(ServerPlayer player, ItemStack contract) {
        if (player == null || contract.isEmpty() || !MaidInfusion.isInfused(contract)) return;

        player.sendSystemMessage(Component.translatable(
                "maid_weapon.message.interior.choose_theme_title"
        ).withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
        player.sendSystemMessage(Component.translatable(
                "maid_weapon.message.interior.choose_theme_hint"
        ).withStyle(ChatFormatting.GRAY));

        for (ContractInteriorTerrainTheme theme : ContractInteriorTerrainTheme.values()) {
            Component button = Component.literal("  [ ")
                    .append(theme.title())
                    .append(Component.literal(" ]"))
                    .withStyle(style -> style
                            .withColor(ChatFormatting.AQUA)
                            .withClickEvent(new ClickEvent(
                                    ClickEvent.Action.RUN_COMMAND,
                                    "/contractinterior choose " + theme.id()
                            ))
                            .withHoverEvent(new HoverEvent(
                                    HoverEvent.Action.SHOW_TEXT,
                                    theme.description()
                            )));
            player.sendSystemMessage(button.append(
                    Component.literal("  ").append(
                            theme.description().copy().withStyle(ChatFormatting.DARK_GRAY)
                    )
            ));
        }
    }

    public static boolean chooseAndEnter(ServerPlayer player, String themeId) {
        if (player == null) return false;

        ItemStack contract = ContractInteriorService.contractForSelection(player);
        if (contract.isEmpty()) {
            player.displayClientMessage(
                    Component.translatable("maid_weapon.message.interior.need_contract"),
                    true
            );
            return false;
        }
        if (!MaidWeaponItem.isOwner(contract, player)) {
            player.displayClientMessage(
                    Component.translatable("maid_weapon.message.not_owner"),
                    true
            );
            return false;
        }

        ContractInteriorTerrainTheme theme =
                ContractInteriorTerrainTheme.byId(themeId).orElse(null);
        if (theme == null) {
            player.displayClientMessage(
                    Component.translatable("maid_weapon.message.interior.theme_unknown"),
                    true
            );
            return false;
        }

        String bindingId = MaidWeaponItem.ensureBindingId(contract);
        ContractInteriorSavedData saved =
                ContractInteriorSavedData.get(player.getServer());
        ContractInteriorSavedData.Plot plot = saved.getOrCreate(bindingId);

        if (plot.hasTerrainTheme()) {
            player.displayClientMessage(
                    Component.translatable(
                            "maid_weapon.message.interior.theme_already_chosen",
                            ContractInteriorTerrainTheme.byId(plot.terrainTheme())
                                    .map(ContractInteriorTerrainTheme::title)
                                    .orElse(Component.literal(plot.terrainTheme()))
                    ),
                    true
            );
            return ContractInteriorService.enter(player, contract);
        }

        if (!saved.chooseTerrain(bindingId, theme.id())) {
            return false;
        }

        player.sendSystemMessage(Component.translatable(
                "maid_weapon.message.interior.theme_chosen",
                theme.title()
        ).withStyle(ChatFormatting.GREEN));

        return ContractInteriorService.enter(player, contract);
    }

    private ContractInteriorSelectionService() {
    }
}
