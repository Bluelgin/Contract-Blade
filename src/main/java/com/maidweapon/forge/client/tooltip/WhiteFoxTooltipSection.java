package com.maidweapon.forge.client.tooltip;
import com.maidweapon.forge.system.mastery.ContractTooltipSnapshot;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import java.util.List;
import java.util.ArrayList;
/** One blade's player-facing tooltip; no client-side gameplay calculations. */
final class WhiteFoxTooltipSection {
    static void append(List<Component> tooltip, CompoundTag profile, boolean expanded) {
        tooltip.add(Component.translatable("maid_weapon.tooltip.compact.white.stats",
                ContractTooltipFormat.percent(ContractTooltipSnapshot.value(profile, "health_bonus")),
                ContractTooltipFormat.percent(ContractTooltipSnapshot.value(profile, "attack_speed_bonus")))
                .withStyle(ChatFormatting.GREEN));
        List<String> learned = new ArrayList<>();
        if (ContractTooltipSnapshot.unlocked(profile, "blessing_cleanse"))
            learned.add(Component.translatable("maid_weapon.tooltip.white.name.cleanse").getString());
        if (ContractTooltipSnapshot.unlocked(profile, "blessing_heal"))
            learned.add(Component.translatable("maid_weapon.tooltip.white.name.heal").getString());
        if (ContractTooltipSnapshot.unlocked(profile, "blessing_edge"))
            learned.add(Component.translatable("maid_weapon.tooltip.white.name.edge").getString());
        if (ContractTooltipSnapshot.unlocked(profile, "blessing_resonance"))
            learned.add(Component.translatable("maid_weapon.tooltip.white.name.resonance").getString());
        if (ContractTooltipSnapshot.unlocked(profile, "blessing_oath"))
            learned.add(Component.translatable("maid_weapon.tooltip.white.name.oath").getString());
        tooltip.add(Component.translatable("maid_weapon.tooltip.white.learned",
                learned.isEmpty() ? Component.translatable("maid_weapon.tooltip.white.name.guard").getString()
                        : String.join(" / ", learned)).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("maid_weapon.tooltip.compact.white.guard",
                ContractTooltipFormat.percent(ContractTooltipSnapshot.value(profile, "guard_reduction")),
                ContractTooltipFormat.decimal(ContractTooltipSnapshot.value(profile, "guard_cooldown") / 20))
                .withStyle(ChatFormatting.AQUA));
        if (expanded) {
            tooltip.add(Component.translatable("maid_weapon.tooltip.section.power"));
            tooltip.add(Component.translatable("maid_weapon.tooltip.white.protection_story").withStyle(ChatFormatting.GRAY));
            if (ContractTooltipSnapshot.unlocked(profile, "blessing_cleanse"))
                tooltip.add(Component.translatable("maid_weapon.tooltip.compact.white.cleanse"));
            if (ContractTooltipSnapshot.unlocked(profile, "blessing_heal"))
                tooltip.add(Component.translatable("maid_weapon.tooltip.compact.white.heal",
                        ContractTooltipFormat.decimal(ContractTooltipSnapshot.value(profile, "heal_amount"))));
            if (ContractTooltipSnapshot.unlocked(profile, "blessing_lantern"))
                tooltip.add(Component.translatable("maid_weapon.tooltip.compact.white.shield"));
            if (ContractTooltipSnapshot.unlocked(profile, "blessing_resonance"))
                tooltip.add(Component.translatable("maid_weapon.tooltip.compact.white.resonance"));
            if (ContractTooltipSnapshot.unlocked(profile, "blessing_mobility"))
                tooltip.add(Component.translatable("maid_weapon.tooltip.white.mobility").withStyle(ChatFormatting.GRAY));
            if (ContractTooltipSnapshot.unlocked(profile, "blessing_edge"))
                tooltip.add(Component.translatable("maid_weapon.tooltip.white.edge").withStyle(ChatFormatting.GRAY));
            if (ContractTooltipSnapshot.unlocked(profile, "blessing_domain"))
                tooltip.add(Component.translatable("maid_weapon.tooltip.white.allies").withStyle(ChatFormatting.GRAY));
            if (ContractTooltipSnapshot.unlocked(profile, "blessing_crisis"))
                tooltip.add(Component.translatable("maid_weapon.tooltip.white.crisis").withStyle(ChatFormatting.GRAY));
            if (ContractTooltipSnapshot.unlocked(profile, "blessing_oath"))
                tooltip.add(Component.translatable("maid_weapon.tooltip.compact.white.oath",
                        ContractTooltipFormat.decimal(ContractTooltipSnapshot.value(profile, "oath_cooldown") / 1200)));
        }
    }


    private WhiteFoxTooltipSection() {}
}
