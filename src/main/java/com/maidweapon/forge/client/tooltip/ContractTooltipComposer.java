package com.maidweapon.forge.client.tooltip;

import com.maidweapon.forge.system.contract.ContractCarrierData;

import com.maidweapon.common.data.MaidWeaponData;
import com.maidweapon.common.system.LoyaltySystem;
import com.maidweapon.forge.compat.SlashBladeCompat;
import com.maidweapon.forge.item.MaidInfusion;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Builds the compact/expanded contract tooltip shared by dedicated and infused weapons.
 *
 * <p>This class is intentionally client-only: {@link Screen#hasShiftDown()} must never leak into
 * common item or dedicated-server code.</p>
 */
public final class ContractTooltipComposer {

    public static void append(ItemStack stack, @Nullable Player viewer, List<Component> tooltip) {
        MaidWeaponData data = MaidInfusion.data(stack);
        var mastery = com.maidweapon.forge.system.mastery.ContractMasteryService.getProgress(viewer);
        boolean stored = MaidInfusion.containsMaid(stack);
        boolean superseded = ContractCarrierData.isContractSuperseded(stack);
        boolean ownerAccess = viewer == null || ContractCarrierData.isOwner(stack, viewer);
        boolean expanded = Screen.hasShiftDown();

        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("maid_weapon.tooltip.maid_name", data.getMaidName()));
        tooltip.add(Component.translatable("maid_weapon.tooltip.resonance",
                data.getResonance(), MaidWeaponData.maximumResonance()));
        tooltip.add(Component.translatable(statusKey(stored, superseded)));

        if (!ownerAccess) {
            tooltip.add(Component.translatable("maid_weapon.tooltip.borrowed_contract_locked"));
        }

        tooltip.add(Component.translatable(expanded
                ? "maid_weapon.tooltip.shift.expanded"
                : "maid_weapon.tooltip.shift.collapsed"));

        if (!expanded) return;

        appendContractSection(stack, data, tooltip);
        appendGrowthSection(mastery, tooltip);
        appendUsageSection(stack, stored, superseded, ownerAccess, tooltip);
    }

    private static void appendContractSection(
            ItemStack stack,
            MaidWeaponData data,
            List<Component> tooltip
    ) {
        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("maid_weapon.tooltip.section.contract"));

        String owner = ContractCarrierData.getOwnerName(stack);
        if (owner != null) {
            tooltip.add(Component.translatable("maid_weapon.tooltip.owner", owner));
        }
        tooltip.add(Component.translatable("maid_weapon.tooltip.favorability",
                data.getFavorability(), LoyaltySystem.getFavorabilityTitle(data.getFavorability())));
    }

    private static void appendGrowthSection(com.maidweapon.common.data.ContractMasteryData mastery, List<Component> tooltip) {
        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("maid_weapon.tooltip.level", mastery.getLevel()));
        tooltip.add(Component.translatable("maid_weapon.tooltip.kills", mastery.getTotalKills()));
        tooltip.add(Component.translatable(
                "maid_weapon.tooltip.unlocked_tier",
                tierName(mastery.getUnlockedTier())
        ));

        if (mastery.getEnderDragonKills() > 0 || mastery.getWitherKills() > 0) {
            tooltip.add(Component.translatable(
                    "maid_weapon.tooltip.boss_kills",
                    mastery.getEnderDragonKills(),
                    mastery.getWitherKills()
            ));
        }

        tooltip.add(Component.translatable(
                "maid_weapon.tooltip.next_upgrade",
                nextUpgradeRequirement(mastery)
        ));

    }

    private static void appendUsageSection(
            ItemStack stack,
            boolean stored,
            boolean superseded,
            boolean ownerAccess,
            List<Component> tooltip
    ) {
        if (superseded || !ownerAccess) return;

        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("maid_weapon.tooltip.section.usage"));

        tooltip.add(Component.translatable(stored ? "maid_weapon.tooltip.call_companion" : "maid_weapon.tooltip.recall_companion",
                com.maidweapon.forge.client.ContractCompanionKeys.CALL.getTranslatedKeyMessage()));
        if (com.maidweapon.common.ContractRulesConfig.AUTO_MANIFEST.get()) {
            tooltip.add(Component.translatable("maid_weapon.tooltip.protect_companion"));
        }

        if (SlashBladeCompat.usesMaidSlashBladeTask(stack)) {
            tooltip.add(Component.translatable(
                    "maid_weapon.tooltip.maid_slashblade_task"
            ));
        }
    }

    private static String statusKey(boolean stored, boolean superseded) {
        if (superseded) return "maid_weapon.tooltip.status.superseded";
        return stored
                ? "maid_weapon.tooltip.status.stored"
                : "maid_weapon.tooltip.status.away";
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
