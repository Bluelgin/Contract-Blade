package com.maidweapon.forge.client.tooltip;

import com.maidweapon.common.data.MaidWeaponData;
import com.maidweapon.common.system.LoyaltySystem;
import com.maidweapon.forge.compat.BladeTetraStoryCompat;
import com.maidweapon.forge.compat.SlashBladeCompat;
import com.maidweapon.forge.item.MaidInfusion;
import com.maidweapon.forge.api.IntrinsicSpiritApi;
import com.maidweapon.forge.system.akatsuki.AkatsukiDomainGrowth;
import com.maidweapon.forge.system.contract.ContractCarrierData;
import com.maidweapon.forge.system.fox.FoxSpiritState;
import com.maidweapon.forge.system.mastery.ContractMasteryService;
import com.maidweapon.forge.system.mastery.ContractTooltipSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
            if (black) appendBlack(tooltip, route, expanded);
            else if (white) appendWhite(tooltip, route, expanded);
            else if (red) appendRed(tooltip, route, expanded);
        } else if (!kind.isEmpty()) {
            tooltip.add(Component.translatable("maid_weapon.tooltip.compact.waiting")
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltip.add(Component.translatable("maid_weapon.tooltip.resonance",
                    data.getResonance(), MaidWeaponData.maximumResonance()));
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
        appendContractSection(stack, data, tooltip);
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

    private static String decimal(double number) {
        return String.format(Locale.ROOT, "%.1f", number);
    }

    private static String percent(double fraction) {
        return decimal(fraction * 100) + "%";
    }

    private static void appendBlack(List<Component> tooltip, CompoundTag profile, boolean expanded) {
        double attack = ContractTooltipSnapshot.value(profile, "weapon_attack_bonus");
        double core = ContractTooltipSnapshot.value(profile, "core_damage_multiplier", 1);
        tooltip.add(Component.translatable("maid_weapon.tooltip.compact.black.attack",
                percent(attack), decimal(core)).withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable("maid_weapon.tooltip.compact.black.skill",
                Component.translatable(ContractTooltipSnapshot.unlocked(profile, "multi_hunter")
                        ? "maid_weapon.tooltip.compact.black.multi"
                        : ContractTooltipSnapshot.unlocked(profile, "combo_mode")
                        ? "maid_weapon.tooltip.compact.black.combo"
                        : "maid_weapon.tooltip.compact.black.barrage"))
                .withStyle(ChatFormatting.GRAY));
        if (expanded) {
            tooltip.add(Component.translatable("maid_weapon.tooltip.section.power"));
            tooltip.add(Component.translatable("maid_weapon.tooltip.compact.black.area",
                    decimal(ContractTooltipSnapshot.value(profile, "barrage_radius", 16)),
                    decimal(ContractTooltipSnapshot.value(profile, "combo_radius", 8))));
            if (ContractTooltipSnapshot.unlocked(profile, "domain_sync"))
                tooltip.add(Component.translatable("maid_weapon.tooltip.compact.black.sync"));
        }
    }

    private static void appendWhite(List<Component> tooltip, CompoundTag profile, boolean expanded) {
        tooltip.add(Component.translatable("maid_weapon.tooltip.compact.white.stats",
                percent(ContractTooltipSnapshot.value(profile, "health_bonus")),
                percent(ContractTooltipSnapshot.value(profile, "attack_speed_bonus")))
                .withStyle(ChatFormatting.GREEN));
        List<String> buffs = new ArrayList<>();
        if (ContractTooltipSnapshot.unlocked(profile, "blessing_heal"))
            buffs.add(Component.translatable("maid_weapon.tooltip.compact.buff.speed").getString());
        if (ContractTooltipSnapshot.unlocked(profile, "blessing_lantern"))
            buffs.add(Component.translatable("maid_weapon.tooltip.compact.buff.regen").getString());
        if (ContractTooltipSnapshot.unlocked(profile, "blessing_crisis"))
            buffs.add(Component.translatable("maid_weapon.tooltip.compact.buff.resist").getString());
        tooltip.add(Component.translatable("maid_weapon.tooltip.compact.white.passives",
                buffs.isEmpty() ? Component.translatable("maid_weapon.tooltip.compact.buff.none").getString()
                        : String.join(" / ", buffs)).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("maid_weapon.tooltip.compact.white.guard",
                percent(ContractTooltipSnapshot.value(profile, "guard_reduction")),
                decimal(ContractTooltipSnapshot.value(profile, "guard_cooldown") / 20))
                .withStyle(ChatFormatting.AQUA));
        if (expanded) {
            tooltip.add(Component.translatable("maid_weapon.tooltip.section.power"));
            if (ContractTooltipSnapshot.unlocked(profile, "blessing_cleanse"))
                tooltip.add(Component.translatable("maid_weapon.tooltip.compact.white.cleanse"));
            if (ContractTooltipSnapshot.unlocked(profile, "blessing_heal"))
                tooltip.add(Component.translatable("maid_weapon.tooltip.compact.white.heal",
                        decimal(ContractTooltipSnapshot.value(profile, "heal_amount"))));
            if (ContractTooltipSnapshot.unlocked(profile, "blessing_lantern"))
                tooltip.add(Component.translatable("maid_weapon.tooltip.compact.white.shield"));
            if (ContractTooltipSnapshot.unlocked(profile, "blessing_resonance"))
                tooltip.add(Component.translatable("maid_weapon.tooltip.compact.white.resonance"));
            if (ContractTooltipSnapshot.unlocked(profile, "blessing_oath"))
                tooltip.add(Component.translatable("maid_weapon.tooltip.compact.white.oath",
                        decimal(ContractTooltipSnapshot.value(profile, "oath_cooldown") / 1200)));
        }
    }

    /**
     * Red Moon presents real server-resolved mastery values, not a claim that damage
     * bypasses a protected boss phase. Aerial recovery only unlocks at Lv.2.
     */
    private static void appendRed(List<Component> tooltip, CompoundTag profile, boolean expanded) {
        double capValue = ContractTooltipSnapshot.value(profile, "space_cut_level_cap");
        Component cap = capValue <= 0
                ? Component.translatable("maid_weapon.tooltip.compact.red.uncapped")
                : Component.translatable("maid_weapon.tooltip.compact.red.cap", decimal(capValue));
        tooltip.add(Component.translatable("maid_weapon.tooltip.red.domain")
                .withStyle(ChatFormatting.DARK_RED));
        tooltip.add(Component.translatable("maid_weapon.tooltip.compact.red.cut",
                percent(ContractTooltipSnapshot.value(profile, "space_cut_multiplier")),
                cap).withStyle(ChatFormatting.RED));

        String ordinary = percent(ContractTooltipSnapshot.value(profile, "soul_recovery_rate"));
        boolean aerial = ContractTooltipSnapshot.unlocked(profile, "akatsuki_swords");
        if (aerial)
            tooltip.add(Component.translatable("maid_weapon.tooltip.red.soul_ready",
                    ordinary, percent(ContractTooltipSnapshot.value(profile, "soul_aerial_rate")))
                    .withStyle(ChatFormatting.GRAY));
        else
            tooltip.add(Component.translatable("maid_weapon.tooltip.red.soul_locked", ordinary)
                    .withStyle(ChatFormatting.GRAY));

        if (!expanded) return;
        tooltip.add(Component.translatable("maid_weapon.tooltip.section.power"));
        tooltip.add(Component.translatable("maid_weapon.tooltip.red.cut_explain")
                .withStyle(ChatFormatting.GRAY));
        if (capValue <= 0)
            tooltip.add(Component.translatable("maid_weapon.tooltip.red.no_level_cap")
                    .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("maid_weapon.tooltip.red.debt_explain")
                .withStyle(ChatFormatting.GRAY));
        if (aerial) {
            tooltip.add(Component.translatable("maid_weapon.tooltip.red.anchor_spawn")
                    .withStyle(ChatFormatting.AQUA));
            tooltip.add(Component.translatable("maid_weapon.tooltip.red.anchor_hit")
                    .withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("maid_weapon.tooltip.red.anchor_locked")
                    .withStyle(ChatFormatting.GRAY));
        }
        if (ContractTooltipSnapshot.unlocked(profile, "akatsuki_cloak"))
            tooltip.add(Component.translatable("maid_weapon.tooltip.red.rescue")
                    .withStyle(ChatFormatting.GRAY));
        appendRedSkillGroup(tooltip, profile, 0, 4);
        appendRedSkillGroup(tooltip, profile, 4, 8);
        appendRedSkillGroup(tooltip, profile, 8, 10);
    }

    /** Three compact sections instead of ten redundant paragraphs at max mastery. */
    private static void appendRedSkillGroup(List<Component> tooltip, CompoundTag profile,
                                            int from, int to) {
        var names = new ArrayList<String>();
        for (int index = from; index < to; index++) {
            String ability = AkatsukiDomainGrowth.ROUTE.get(index);
            if (ContractTooltipSnapshot.unlocked(profile, "akatsuki_" + ability))
                names.add(Component.translatable("maid_weapon.tooltip.red.skill." + ability).getString());
        }
        if (!names.isEmpty())
            tooltip.add(Component.translatable("maid_weapon.tooltip.red.skill_group",
                    from + 1, to, String.join(" · ", names)).withStyle(ChatFormatting.DARK_GRAY));
    }

    private static void appendContractSection(ItemStack stack, MaidWeaponData data, List<Component> tooltip) {
        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("maid_weapon.tooltip.section.contract"));
        String owner = ContractCarrierData.getOwnerName(stack);
        if (owner != null) tooltip.add(Component.translatable("maid_weapon.tooltip.owner", owner));
        tooltip.add(Component.translatable("maid_weapon.tooltip.favorability",
                data.getFavorability(), LoyaltySystem.getFavorabilityTitle(data.getFavorability())));
        tooltip.add(Component.translatable("maid_weapon.tooltip.resonance",
                data.getResonance(), MaidWeaponData.maximumResonance()));
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
