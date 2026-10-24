package com.maidweapon.forge.client.tooltip;
import com.maidweapon.forge.system.mastery.ContractTooltipSnapshot;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import java.util.List;
/** One blade's player-facing tooltip; no client-side gameplay calculations. */
final class AkatsukiTooltipSection {
    static void append(List<Component> tooltip, CompoundTag profile, boolean expanded) {
        if (!expanded) return;
        SimpleTooltipPresentation.line(tooltip, "maid_weapon.tooltip.red.domain_story");
        if (!ContractTooltipSnapshot.unlocked(profile, "akatsuki_garden")) return;
        double capValue = ContractTooltipSnapshot.value(profile, "space_cut_level_cap");
        Component cap = capValue <= 0
                ? Component.translatable("maid_weapon.tooltip.compact.red.uncapped")
                : Component.translatable("maid_weapon.tooltip.compact.red.cap", ContractTooltipFormat.decimal(capValue));
        tooltip.add(Component.translatable("maid_weapon.tooltip.compact.red.cut",
                ContractTooltipFormat.percent(ContractTooltipSnapshot.value(profile, "space_cut_multiplier")),
                cap).withStyle(ChatFormatting.RED));
        if (ContractTooltipSnapshot.value(profile, "resonance_hit_ratio") > 0)
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
            tooltip.add(Component.translatable("maid_weapon.tooltip.fox.red.soul_basic", ordinary)
                    .withStyle(ChatFormatting.GRAY));

        tooltip.add(Component.translatable("maid_weapon.tooltip.red.soul_story").withStyle(ChatFormatting.GRAY));
        if (capValue <= 0)
            tooltip.add(Component.translatable("maid_weapon.tooltip.red.no_level_cap")
                    .withStyle(ChatFormatting.GRAY));
        if (aerial) {
            tooltip.add(Component.translatable("maid_weapon.tooltip.red.anchor_spawn")
                    .withStyle(ChatFormatting.AQUA));
            tooltip.add(Component.translatable("maid_weapon.tooltip.red.anchor_hit")
                    .withStyle(ChatFormatting.GRAY));
        }
        if (ContractTooltipSnapshot.unlocked(profile, "akatsuki_cloak"))
            tooltip.add(Component.translatable("maid_weapon.tooltip.red.rescue")
                    .withStyle(ChatFormatting.GRAY));
        for (String feature : new String[]{"ring", "tracking", "adaptation", "counter", "chain", "rain", "complete"})
            if (ContractTooltipSnapshot.unlocked(profile, "akatsuki_" + feature))
                SimpleTooltipPresentation.line(tooltip, "maid_weapon.tooltip.fox.red." + feature);
    }

    private AkatsukiTooltipSection() {}
}
