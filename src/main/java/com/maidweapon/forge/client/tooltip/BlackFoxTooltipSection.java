package com.maidweapon.forge.client.tooltip;
import com.maidweapon.forge.system.mastery.ContractTooltipSnapshot;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import java.util.List;

/** One blade's player-facing tooltip; no client-side gameplay calculations. */
final class BlackFoxTooltipSection {
    static void append(List<Component> tooltip, CompoundTag profile, boolean expanded) {
        if (!expanded) return;
        tooltip.add(Component.translatable("maid_weapon.tooltip.fox.black.condition").withStyle(ChatFormatting.GRAY));
        double attack = ContractTooltipSnapshot.value(profile, "weapon_attack_bonus");
        double core = ContractTooltipSnapshot.value(profile, "core_damage_multiplier", 1);
        tooltip.add(Component.translatable("maid_weapon.tooltip.compact.black.attack",
                ContractTooltipFormat.percent(attack), ContractTooltipFormat.decimal(core)).withStyle(ChatFormatting.LIGHT_PURPLE));
        boolean summoned = ContractTooltipSnapshot.unlocked(profile, "summon_core");
        if (expanded) {
            if (summoned)
                tooltip.add(Component.translatable("maid_weapon.tooltip.black.core_story").withStyle(ChatFormatting.GRAY));
            if (ContractTooltipSnapshot.unlocked(profile, "combo_mode"))
                tooltip.add(Component.translatable("maid_weapon.tooltip.black.combo_story").withStyle(ChatFormatting.GRAY));
            if (ContractTooltipSnapshot.unlocked(profile, "owner_follow"))
                tooltip.add(Component.translatable("maid_weapon.tooltip.black.follow_story").withStyle(ChatFormatting.GRAY));
            if (ContractTooltipSnapshot.unlocked(profile, "multi_hunter"))
                tooltip.add(Component.translatable("maid_weapon.tooltip.black.multi_story").withStyle(ChatFormatting.GRAY));
            if (summoned)
                tooltip.add(Component.translatable("maid_weapon.tooltip.compact.black.area",
                        ContractTooltipFormat.decimal(ContractTooltipSnapshot.value(profile, "barrage_radius", 16)),
                        ContractTooltipFormat.decimal(ContractTooltipSnapshot.value(profile, "combo_radius", 8))));
            if (ContractTooltipSnapshot.unlocked(profile, "domain_sync"))
                tooltip.add(Component.translatable("maid_weapon.tooltip.compact.black.sync"));
            for (String feature : new String[]{"mark_priority", "protect_owner", "chain_attack", "phantom_blade",
                    "phantom_blade_vertical", "focus_target", "wave_edge", "retaliate_owner"})
                if (ContractTooltipSnapshot.unlocked(profile, feature))
                    SimpleTooltipPresentation.line(tooltip, "maid_weapon.tooltip.fox.black." + feature);
        }
    }


    private BlackFoxTooltipSection() {}
}
