package com.maidweapon.forge.client.tooltip;

import com.maidweapon.forge.system.contract.ContractCarrierData;

import com.maidweapon.common.data.MaidWeaponData;
import com.maidweapon.common.legacy.LegacySinArchive;
import com.maidweapon.common.system.LoyaltySystem;
import com.maidweapon.forge.compat.SlashBladeCompat;
import com.maidweapon.forge.item.MaidInfusion;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;

/**
 * Builds the compact/expanded contract tooltip shared by dedicated and infused weapons.
 *
 * <p>This class is intentionally client-only: {@link Screen#hasShiftDown()} must never leak into
 * common item or dedicated-server code.</p>
 */
public final class ContractTooltipComposer {

    public static void append(ItemStack stack, @Nullable Player viewer, List<Component> tooltip) {
        MaidWeaponData data = MaidInfusion.data(stack);
        boolean dedicated = MaidInfusion.isContractBlade(stack);
        boolean stored = MaidInfusion.containsMaid(stack);
        boolean superseded = ContractCarrierData.isContractSuperseded(stack);
        boolean ownerAccess = viewer == null || ContractCarrierData.isOwner(stack, viewer);
        boolean expanded = Screen.hasShiftDown();

        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("maid_weapon.tooltip.title"));
        tooltip.add(Component.translatable("maid_weapon.tooltip.maid_name", data.getMaidName()));
        tooltip.add(Component.translatable("maid_weapon.tooltip.resonance",
                data.getResonance(), MaidWeaponData.maximumResonance()));
        tooltip.add(Component.translatable(statusKey(stored, superseded)));

        if (superseded) {
            tooltip.add(Component.translatable("maid_weapon.tooltip.superseded_contract"));
        }
        if (!ownerAccess) {
            tooltip.add(Component.translatable("maid_weapon.tooltip.borrowed_contract_locked"));
        }

        tooltip.add(Component.translatable(expanded
                ? "maid_weapon.tooltip.shift.expanded"
                : "maid_weapon.tooltip.shift.collapsed"));

        if (!expanded) return;

        appendContractSection(stack, data, tooltip);
        appendGrowthSection(data, tooltip);
        appendPowerSection(data, dedicated, ownerAccess, tooltip);
        appendUsageSection(stack, stored, superseded, dedicated, ownerAccess, tooltip);
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
        tooltip.add(Component.translatable("maid_weapon.tooltip.level", data.getLevel()));
        tooltip.add(Component.translatable("maid_weapon.tooltip.favorability",
                data.getFavorability(), LoyaltySystem.getFavorabilityTitle(data.getFavorability())));
    }

    private static void appendGrowthSection(MaidWeaponData data, List<Component> tooltip) {
        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("maid_weapon.tooltip.section.growth"));
        tooltip.add(Component.translatable("maid_weapon.tooltip.kills", data.getTotalKills()));
        tooltip.add(Component.translatable(
                "maid_weapon.tooltip.unlocked_tier",
                tierName(data.getUnlockedTier())
        ));

        if (data.getEnderDragonKills() > 0 || data.getWitherKills() > 0) {
            tooltip.add(Component.translatable(
                    "maid_weapon.tooltip.boss_kills",
                    data.getEnderDragonKills(),
                    data.getWitherKills()
            ));
        }

        tooltip.add(Component.translatable(
                "maid_weapon.tooltip.next_upgrade",
                nextUpgradeRequirement(data)
        ));

        if (LegacySinArchive.hasData(data)) {
            tooltip.add(Component.translatable(
                    "maid_weapon.tooltip.legacy_sin_data",
                    LegacySinArchive.entryCount(data)
            ));
        }
    }

    private static void appendPowerSection(
            MaidWeaponData data,
            boolean dedicated,
            boolean ownerAccess,
            List<Component> tooltip
    ) {
        if (!ownerAccess) return;

        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("maid_weapon.tooltip.section.power"));
        tooltip.add(Component.translatable(
                "maid_weapon.tooltip.level_damage_bonus",
                oneDecimal(data.getAttackDamageBonus())
        ));
        tooltip.add(Component.translatable(
                "maid_weapon.tooltip.favorability_bonus",
                oneDecimal((data.getFavorabilityDamageMultiplier() - 1.0f) * 100.0f)
        ));

        if (dedicated) {
            float totalDamage =
                    (3.0f + data.getAttackDamageBonus()) * data.getFavorabilityDamageMultiplier();
            tooltip.add(Component.translatable(
                    "maid_weapon.tooltip.dedicated_damage",
                    oneDecimal(totalDamage)
            ));
        } else {
            tooltip.add(Component.translatable(
                    "maid_weapon.tooltip.additive_damage",
                    oneDecimal(MaidInfusion.getGenericDamageBonus(data))
            ));
        }
    }

    private static void appendUsageSection(
            ItemStack stack,
            boolean stored,
            boolean superseded,
            boolean dedicated,
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
                    "maid_weapon.tooltip.maid_slashblade_task",
                    SlashBladeCompat.getMaidSlashBladeProviderName()
            ));
        }
    }

    private static String statusKey(boolean stored, boolean superseded) {
        if (superseded) return "maid_weapon.tooltip.status.superseded";
        return stored
                ? "maid_weapon.tooltip.status.stored"
                : "maid_weapon.tooltip.status.manifested";
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

    private static Component nextUpgradeRequirement(MaidWeaponData data) {
        int level = data.getLevel();
        if (level >= MaidWeaponData.MAX_LEVEL) {
            return Component.translatable("maid_weapon.tooltip.upgrade.max");
        }
        if (com.maidweapon.common.ContractRulesConfig.LEVELS[level + 1].custom()) {
            var rule = com.maidweapon.common.ContractRulesConfig.LEVELS[level + 1];
            String target = rule.mode().get().equals("entities")
                    ? String.join(" / ", rule.entities().get()) : "TIER " + rule.tier().get();
            return Component.translatable("maid_weapon.tooltip.upgrade.configured",
                    target, data.getNextUpgradeProgress(), rule.kills().get());
        }
        return switch (level) {
            case 5 -> Component.translatable("maid_weapon.tooltip.upgrade.dragon");
            case 6 -> Component.translatable("maid_weapon.tooltip.upgrade.wither");
            case 7 -> Component.translatable("maid_weapon.tooltip.upgrade.boss_each_three");
            case 8 -> Component.translatable("maid_weapon.tooltip.upgrade.boss_combined_five");
            case 9 -> Component.translatable("maid_weapon.tooltip.upgrade.boss_combined_ten");
            default -> Component.translatable(
                    "maid_weapon.tooltip.upgrade.tier",
                    MaidWeaponData.TIER_REQUIREMENT[level - 1]
            );
        };
    }

    private static String oneDecimal(float value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private ContractTooltipComposer() {
    }
}
