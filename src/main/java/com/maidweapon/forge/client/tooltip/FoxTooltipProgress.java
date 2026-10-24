package com.maidweapon.forge.client.tooltip;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Presentation of the next server-resolved milestone, never a client-side unlock calculation. */
final class FoxTooltipProgress {
    static void append(List<Component> lines, CompoundTag profile) {
        int level = profile.getInt("next_level");
        var features = profile.getList("next_features", Tag.TAG_STRING);
        if (level <= 0 || features.isEmpty()) return;
        MutableComponent names = Component.empty();
        for (int i = 0; i < features.size(); i++) {
            if (i > 0) names.append(" / ");
            names.append(Component.translatable("maid_weapon.tooltip.feature." + features.getString(i)));
        }
        lines.add(Component.translatable("maid_weapon.tooltip.fox.next", names, level)
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    private FoxTooltipProgress() {}
}
