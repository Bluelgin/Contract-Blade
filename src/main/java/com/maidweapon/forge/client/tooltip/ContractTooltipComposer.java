package com.maidweapon.forge.client.tooltip;

import com.maidweapon.common.data.MaidWeaponData;
import com.maidweapon.common.system.LoyaltySystem;
import com.maidweapon.forge.compat.BladeTetraStoryCompat;
import com.maidweapon.forge.compat.SlashBladeCompat;
import com.maidweapon.forge.item.MaidInfusion;
import com.maidweapon.forge.api.IntrinsicSpiritApi;
import com.maidweapon.forge.system.contract.ContractCarrierData;
import com.maidweapon.forge.system.fox.FoxSpiritState;
import com.maidweapon.forge.system.mastery.ContractMasteryService;
import com.maidweapon.forge.system.mastery.ContractTooltipSnapshot;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** One client-only presentation composer for any infused carrier and its special abilities. */
public final class ContractTooltipComposer {
    public static void append(ItemStack stack, @Nullable Player viewer, List<Component> tooltip) {
        MaidWeaponData data = MaidInfusion.data(stack);
        var mastery = ContractMasteryService.getProgress(viewer);
        boolean transferred = ContractCarrierData.isContractSuperseded(stack);
        boolean ownerAccess = viewer != null && ContractCarrierData.isOwner(stack, viewer);
        boolean expanded = Screen.hasShiftDown();
        String spirit = FoxSpiritState.resident(stack).getString("SpiritId");
        boolean black = FoxSpiritState.BLACK.equals(spirit) && FoxSpiritState.isOriginalHome(stack);
        boolean white = FoxSpiritState.WHITE.equals(spirit) && FoxSpiritState.isOriginalHome(stack);
        boolean red = BladeTetraStoryCompat.isAkatsuki(stack)
                && IntrinsicSpiritApi.hasIntrinsicSpirit(stack, "blade_tetra:akatsuki");
        // The intrinsic Akatsuki channel can be archived independently of ordinary maid
        // storage. Never claim that an archived (or manifested) spirit is stationed.
        boolean stored = red
                ? IntrinsicSpiritApi.stored(stack, "blade_tetra:akatsuki")
                : MaidInfusion.containsMaid(stack);

        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("maid_weapon.tooltip.compact.identity",
                mastery.getLevel(), data.getMaidName(),
                Component.translatable(transferred ? "maid_weapon.tooltip.compact.transferred"
                        : stored ? "maid_weapon.tooltip.compact.stored"
                        : "maid_weapon.tooltip.compact.away")).withStyle(ChatFormatting.GRAY));

        String kind = black ? "black" : white ? "white" : red ? "red" : "";
        CompoundTag route = kind.isEmpty() ? new CompoundTag() : ContractTooltipSnapshot.route(viewer, kind);
        boolean known = route.getBoolean("known");
        if (known) {
            tooltip.add(Component.translatable("maid_weapon.tooltip.lore." + kind)
                    .withStyle(ChatFormatting.GRAY));
            if (black) BlackFoxTooltipSection.append(tooltip, route, expanded);
            else if (white) WhiteFoxTooltipSection.append(tooltip, route, expanded);
            else if (red) AkatsukiTooltipSection.append(tooltip, route, expanded);
        } else if (!kind.isEmpty()) {
            tooltip.add(Component.translatable("maid_weapon.tooltip.compact.waiting")
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else {
            appendResonance(stack, viewer, tooltip);
        }

        if (red && !stored) {
            tooltip.add(Component.translatable("maid_weapon.tooltip.red.spirit_away")
                    .withStyle(ChatFormatting.GRAY));
        } else if (red && (viewer == null || !sameCarrier(stack, viewer.getMainHandItem()))) {
            tooltip.add(Component.translatable("maid_weapon.tooltip.red.not_mainhand")
                    .withStyle(ChatFormatting.GRAY));
        } else if (red && (!ownerAccess || transferred || !active(viewer, stack, kind))) {
            tooltip.add(Component.translatable("maid_weapon.tooltip.red.not_authorized")
                    .withStyle(ChatFormatting.GRAY));
        } else if (!kind.isEmpty() && (!ownerAccess || !stored || transferred
                || !active(viewer, stack, kind))) {
            tooltip.add(Component.translatable("maid_weapon.tooltip.compact.inactive")
                    .withStyle(ChatFormatting.GRAY));
        } else if (!kind.isEmpty() && known) {
            tooltip.add(Component.translatable("maid_weapon.tooltip.compact.equipped")
                    .withStyle(ChatFormatting.DARK_GREEN));
        }
        if (!ownerAccess && viewer != null) {
            tooltip.add(Component.translatable("maid_weapon.tooltip.borrowed_contract_locked")
                    .withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable(expanded
                ? "maid_weapon.tooltip.shift.expanded"
                : "maid_weapon.tooltip.shift.collapsed"));

        if (!expanded) return;
        appendContractSection(stack, viewer, data, tooltip);
        appendGrowthSection(mastery, tooltip);
        if (FoxSpiritState.hasResident(stack) && !FoxSpiritState.isOriginalHome(stack)) {
            tooltip.add(Component.translatable(FoxSpiritState.BLACK.equals(spirit)
                    ? "maid_weapon.fox.black.tooltip.away"
                    : "maid_weapon.fox.tooltip.away").withStyle(ChatFormatting.GRAY));
        }
        appendUsageSection(stack, stored, transferred, ownerAccess, tooltip);
    }

    /** Never label a backpack blade as active just because another blade is equipped. */
    private static boolean active(@Nullable Player viewer, ItemStack stack, String kind) {
        if (viewer == null) return false;
        var view = ContractTooltipSnapshot.view(viewer);
        if (!view.getBoolean(kind + "_equipped")) return false;
        return switch (kind) {
            case "white" -> sameCarrier(stack, viewer.getMainHandItem())
                    || sameCarrier(stack, viewer.getOffhandItem());
            case "black", "red" -> sameCarrier(stack, viewer.getMainHandItem());
            default -> false;
        };
    }

    private static boolean sameCarrier(ItemStack a, ItemStack b) {
        String id = ContractCarrierData.getBindingId(a);
        return id != null && !id.isBlank()
                && id.equals(ContractCarrierData.getBindingId(b))
                && ItemStack.isSameItemSameTags(a, b);
    }

    private static void appendContractSection(ItemStack stack, Player viewer, MaidWeaponData data, List<Component> tooltip) {
        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("maid_weapon.tooltip.section.contract"));
        String owner = ContractCarrierData.getOwnerName(stack);
        if (owner != null) tooltip.add(Component.translatable("maid_weapon.tooltip.owner", owner));
        tooltip.add(Component.translatable("maid_weapon.tooltip.favorability",
                data.getFavorability(), LoyaltySystem.getFavorabilityTitle(data.getFavorability())));
        appendResonance(stack, viewer, tooltip);
    }

    /** Server-confirmed owner snapshot; stale item NBT is never shown as current. */
    private static void appendResonance(ItemStack stack, Player viewer, List<Component> tooltip) {
        String maid = ContractCarrierData.getBoundMaidUUID(stack);
        int value = com.maidweapon.forge.network.ResonanceNetwork.clientCurrent(viewer, maid);
        int ceiling = com.maidweapon.forge.network.ResonanceNetwork.clientMaximum(viewer, maid);
        if (value < 0 || ceiling <= 0)
            tooltip.add(Component.translatable("maid_weapon.tooltip.resonance.waiting")
                    .withStyle(ChatFormatting.DARK_GRAY));
        else tooltip.add(Component.translatable("maid_weapon.tooltip.resonance", value, ceiling));
    }

    private static void appendGrowthSection(com.maidweapon.common.data.ContractMasteryData mastery, List<Component> tooltip) {
        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("maid_weapon.tooltip.section.growth"));
        tooltip.add(Component.translatable("maid_weapon.tooltip.kills", mastery.getTotalKills()));
        tooltip.add(Component.translatable("maid_weapon.tooltip.unlocked_tier", tierName(mastery.getUnlockedTier())));
        if (mastery.getEnderDragonKills() > 0 || mastery.getWitherKills() > 0)
            tooltip.add(Component.translatable("maid_weapon.tooltip.boss_kills",
                    mastery.getEnderDragonKills(), mastery.getWitherKills()));
        tooltip.add(Component.translatable("maid_weapon.tooltip.next_upgrade", nextUpgradeRequirement(mastery)));
    }

    private static void appendUsageSection(ItemStack stack, boolean stored, boolean transferred,
            boolean ownerAccess, List<Component> tooltip) {
        if (transferred || !ownerAccess) return;
        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("maid_weapon.tooltip.section.usage"));
        tooltip.add(Component.translatable(stored ? "maid_weapon.tooltip.call_companion"
                : "maid_weapon.tooltip.recall_companion",
                com.maidweapon.forge.client.ContractCompanionKeys.CALL.getTranslatedKeyMessage()));
        if (SlashBladeCompat.usesMaidSlashBladeTask(stack))
            tooltip.add(Component.translatable("maid_weapon.tooltip.maid_slashblade_task"));
    }

    private static Component tierName(int tier) {
        return Component.translatable(switch (tier) {
            case MaidWeaponData.TIER_1 -> "maid_weapon.tooltip.tier.1";
            case MaidWeaponData.TIER_2 -> "maid_weapon.tooltip.tier.2";
            case MaidWeaponData.TIER_3 -> "maid_weapon.tooltip.tier.3";
            case MaidWeaponData.TIER_4 -> "maid_weapon.tooltip.tier.4";
            case MaidWeaponData.TIER_5 -> "maid_weapon.tooltip.tier.5";
            default -> "maid_weapon.tooltip.tier.none";
        });
    }

    private static Component nextUpgradeRequirement(com.maidweapon.common.data.ContractMasteryData mastery) {
        int level = mastery.getLevel();
        if (level >= MaidWeaponData.MAX_LEVEL) {
            return Component.translatable("maid_weapon.tooltip.upgrade.max");
        }
        if (com.maidweapon.common.ContractRulesConfig.LEVELS[level + 1].custom()) {
            var rule = com.maidweapon.common.ContractRulesConfig.LEVELS[level + 1];
            Component target = rule.mode().get().equals("entities")
                    ? Component.translatable("maid_weapon.tooltip.upgrade.targets") : tierName(rule.tier().get());
            return Component.translatable("maid_weapon.tooltip.upgrade.configured",
                    target, mastery.getNextUpgradeProgress(), rule.kills().get());
        }
        return switch (level) {
            case 5 -> Component.translatable("maid_weapon.tooltip.upgrade.dragon");
            case 6 -> Component.translatable("maid_weapon.tooltip.upgrade.wither");
            case 7 -> Component.translatable("maid_weapon.tooltip.upgrade.boss_each_three");
            case 8 -> Component.translatable("maid_weapon.tooltip.upgrade.boss_combined_five");
            case 9 -> Component.translatable("maid_weapon.tooltip.upgrade.boss_combined_ten");
            default -> Component.translatable(
                    "maid_weapon.tooltip.upgrade.tier",
                    com.maidweapon.common.data.ContractMasteryData.TIER_REQUIREMENT[level - 1]
            );
        };
    }

    private ContractTooltipComposer() {
    }
}
