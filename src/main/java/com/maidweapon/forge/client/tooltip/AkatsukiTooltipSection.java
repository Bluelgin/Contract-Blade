package com.maidweapon.forge.client.tooltip;
import com.maidweapon.forge.system.mastery.ContractTooltipSnapshot;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import java.util.List;
import java.util.ArrayList;
import com.maidweapon.forge.system.akatsuki.AkatsukiDomainGrowth;
/** One blade's player-facing tooltip; no client-side gameplay calculations. */
final class AkatsukiTooltipSection {
    static void append(List<Component> tooltip, CompoundTag profile, boolean expanded) {
        double capValue = ContractTooltipSnapshot.value(profile, "space_cut_level_cap");
        Component cap = capValue <= 0
                ? Component.translatable("maid_weapon.tooltip.compact.red.uncapped")
                : Component.translatable("maid_weapon.tooltip.compact.red.cap", ContractTooltipFormat.decimal(capValue));
        tooltip.add(Component.translatable("maid_weapon.tooltip.red.domain")
                .withStyle(ChatFormatting.DARK_RED));
        tooltip.add(Component.translatable("maid_weapon.tooltip.compact.red.cut",
                ContractTooltipFormat.percent(ContractTooltipSnapshot.value(profile, "space_cut_multiplier")),
                cap).withStyle(ChatFormatting.RED));
        tooltip.add(Component.translatable("maid_weapon.tooltip.red.resonance",
                ContractTooltipFormat.percent(ContractTooltipSnapshot.value(profile, "resonance_hit_ratio")))
                .withStyle(ChatFormatting.GOLD));

        String ordinary = ContractTooltipFormat.percent(ContractTooltipSnapshot.value(profile, "soul_recovery_rate"));
        boolean aerial = ContractTooltipSnapshot.unlocked(profile, "akatsuki_swords");
        if (aerial)
            tooltip.add(Component.translatable("maid_weapon.tooltip.red.soul_ready",
                    ordinary, ContractTooltipFormat.percent(ContractTooltipSnapshot.value(profile, "soul_aerial_rate")))
                    .withStyle(ChatFormatting.GRAY));
        else
            tooltip.add(Component.translatable("maid_weapon.tooltip.red.soul_locked", ordinary)
                    .withStyle(ChatFormatting.GRAY));

        if (!expanded) return;
        tooltip.add(Component.translatable("maid_weapon.tooltip.section.power"));
        tooltip.add(Component.translatable("maid_weapon.tooltip.red.cut_explain")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("maid_weapon.tooltip.red.resonance_explain")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("maid_weapon.tooltip.red.channel_explain")
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


    private AkatsukiTooltipSection() {}
}
