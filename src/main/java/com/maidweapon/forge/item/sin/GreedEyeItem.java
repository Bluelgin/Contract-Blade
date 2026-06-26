package com.maidweapon.forge.item.sin;

import com.maidweapon.common.sin.SinType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import javax.annotation.Nullable;
import java.util.List;

public class GreedEyeItem extends SinBaseItem {
    public GreedEyeItem() { super(SinType.GREED, new Properties()); }

    @Override
    protected void appendSinTooltip(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.empty());
        tooltip.add(Component.literal("§8§l✦ 漆黑契约·索求 ✦"));
        tooltip.add(Component.literal("§7契约上写着许多名字——但你一个都不认识。"));
        tooltip.add(Component.literal("§8但你能感觉到——这只是碎片。"));
        tooltip.add(Component.empty());
        tooltip.add(Component.literal("§8◆ 击杀掉落 +50%  |  经验 ×2"));
        tooltip.add(Component.literal("§c◇ 武器升级进度减半"));
    }
}
