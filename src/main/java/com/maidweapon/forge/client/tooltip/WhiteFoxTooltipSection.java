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
                ContractTooltipFormat.percent(ContractTooltipSnapshot.value(profile, "guard_reduction")),
                ContractTooltipFormat.decimal(ContractTooltipSnapshot.value(profile, "guard_cooldown") / 20))
                .withStyle(ChatFormatting.AQUA));
        if (expanded) {
            tooltip.add(Component.translatable("maid_weapon.tooltip.section.power"));
            if (ContractTooltipSnapshot.unlocked(profile, "blessing_cleanse"))
                tooltip.add(Component.translatable("maid_weapon.tooltip.compact.white.cleanse"));
            if (ContractTooltipSnapshot.unlocked(profile, "blessing_heal"))
                tooltip.add(Component.translatable("maid_weapon.tooltip.compact.white.heal",
                        ContractTooltipFormat.decimal(ContractTooltipSnapshot.value(profile, "heal_amount"))));
            if (ContractTooltipSnapshot.unlocked(profile, "blessing_lantern"))
                tooltip.add(Component.translatable("maid_weapon.tooltip.compact.white.shield"));
            if (ContractTooltipSnapshot.unlocked(profile, "blessing_resonance"))
                tooltip.add(Component.translatable("maid_weapon.tooltip.compact.white.resonance"));
            if (ContractTooltipSnapshot.unlocked(profile, "blessing_oath"))
                tooltip.add(Component.translatable("maid_weapon.tooltip.compact.white.oath",
                        ContractTooltipFormat.decimal(ContractTooltipSnapshot.value(profile, "oath_cooldown") / 1200)));
        }
    }


    private WhiteFoxTooltipSection() {}
}
