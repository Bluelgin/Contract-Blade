package com.maidweapon.forge.item.sin;

import com.maidweapon.common.sin.SinType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import javax.annotation.Nullable;
import java.util.List;

public class LustChainItem extends SinBaseItem {
    public LustChainItem() { super(SinType.LUST, new Properties()); }

    @Override
    protected void appendSinTooltip(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.empty());
        tooltip.add(Component.literal("§d§l✦ 桃色桃符·倾心 ✦"));
        tooltip.add(Component.literal("§7符纸的背面画着一颗歪歪扭扭的心。"));
        tooltip.add(Component.literal("§8但你能感觉到——这只是碎片。"));
        tooltip.add(Component.empty());
        tooltip.add(Component.literal("§d◆ 攻击新种类怪物 +25%"));
        tooltip.add(Component.literal("§c◇ 好感度自然恢复降为0"));
    }
}
