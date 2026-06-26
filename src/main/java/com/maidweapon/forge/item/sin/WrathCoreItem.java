package com.maidweapon.forge.item.sin;

import com.maidweapon.common.sin.SinType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import javax.annotation.Nullable;
import java.util.List;

/**
 * 红之刻印·崩坏 — 暴怒
 * 宵触碰过的罪孽之一。愤怒会伤害最亲近的人。
 */
public class WrathCoreItem extends SinBaseItem {
    public WrathCoreItem() { super(SinType.WRATH, new Properties()); }

    @Override
    protected void appendSinTooltip(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.empty());
        tooltip.add(Component.literal("§c§l✦ 红之刻印·崩坏 ✦"));
        tooltip.add(Component.literal("§7刻印的边缘微微发烫。像愤怒从未冷却。"));
        tooltip.add(Component.literal("§8但你能感觉到——这只是碎片。"));
        tooltip.add(Component.empty());
        tooltip.add(Component.literal("§c◆ 连击递增 +10%/层，最高5层"));
        tooltip.add(Component.literal("§c◇ 好感度消耗 ×3"));
    }
}

