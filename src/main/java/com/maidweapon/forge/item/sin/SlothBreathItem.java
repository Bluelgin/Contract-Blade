package com.maidweapon.forge.item.sin;

import com.maidweapon.common.sin.SinType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import javax.annotation.Nullable;
import java.util.List;

public class SlothBreathItem extends SinBaseItem {
    public SlothBreathItem() { super(SinType.SLOTH, new Properties()); }

    @Override
    protected void appendSinTooltip(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.empty());
        tooltip.add(Component.literal("§9§l✦ 苍蓝结界·停滞 ✦"));
        tooltip.add(Component.literal("§7结界之内，连风都是静止的。"));
        tooltip.add(Component.literal("§8但你能感觉到——这只是碎片。"));
        tooltip.add(Component.empty());
        tooltip.add(Component.literal("§9◆ 站桩3秒后：每秒恢复2❤ + 好感度+1"));
        tooltip.add(Component.literal("§c◇ 移速 -15%  |  攻速 -20%"));
    }
}
