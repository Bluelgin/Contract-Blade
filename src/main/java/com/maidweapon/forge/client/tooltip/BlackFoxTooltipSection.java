package com.maidweapon.forge.client.tooltip;
import com.maidweapon.forge.system.mastery.ContractTooltipSnapshot;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import java.util.List;

/** One blade's player-facing tooltip; no client-side gameplay calculations. */
final class BlackFoxTooltipSection {
    static void append(List<Component> tooltip, CompoundTag profile, boolean expanded) {
        double attack = ContractTooltipSnapshot.value(profile, "weapon_attack_bonus");
        double core = ContractTooltipSnapshot.value(profile, "core_damage_multiplier", 1);
        tooltip.add(Component.translatable("maid_weapon.tooltip.compact.black.attack",
                ContractTooltipFormat.percent(attack), ContractTooltipFormat.decimal(core)).withStyle(ChatFormatting.LIGHT_PURPLE));
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
                    ContractTooltipFormat.decimal(ContractTooltipSnapshot.value(profile, "barrage_radius", 16)),
                    ContractTooltipFormat.decimal(ContractTooltipSnapshot.value(profile, "combo_radius", 8))));
            if (ContractTooltipSnapshot.unlocked(profile, "domain_sync"))
                tooltip.add(Component.translatable("maid_weapon.tooltip.compact.black.sync"));
        }
    }


    private BlackFoxTooltipSection() {}
}
