package com.maidweapon.forge.item.sin;

import com.maidweapon.common.sin.SinType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import javax.annotation.Nullable;
import java.util.List;

/**
 * 紫御守·孤高 — 傲慢
 * 攻击+50%，受伤+30%。嵌入后可解锁所有罪恶槽位。
 */
public class PrideCrownItem extends SinBaseItem {
    public PrideCrownItem() { super(SinType.PRIDE, new Properties()); }

    @Override
    protected void appendSinTooltip(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.empty());
        tooltip.add(Component.literal("§5§l✦ 紫御守·孤高 ✦"));
        tooltip.add(Component.literal("§7触手温润，像是被什么人长久佩戴过。"));
        tooltip.add(Component.literal("§8但你能感觉到——这只是碎片。"));
        tooltip.add(Component.empty());
        tooltip.add(Component.literal("§5◆ 攻击 +50%  |  受伤 +30%"));
        tooltip.add(Component.literal("§8解锁全部罪恶槽位"));
    }
}
