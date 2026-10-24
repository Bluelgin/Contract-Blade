package com.maidweapon.forge.client.tooltip;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** Two mutually exclusive pages; callers retain identity/state outside this block. */
public final class SimpleTooltipPresentation {
    public static void append(List<Component> tooltip, boolean expanded, String lore,
                              Consumer<List<Component>> details) {
        if (expanded) details.accept(tooltip);
        else {
            tooltip.add(Component.translatable(lore).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("maid_weapon.tooltip.simple.shift").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    static void line(List<Component> tooltip, String key, Object... arguments) {
        tooltip.add(Component.translatable(key, arguments).withStyle(ChatFormatting.GRAY));
    }

    private SimpleTooltipPresentation() { }
}
