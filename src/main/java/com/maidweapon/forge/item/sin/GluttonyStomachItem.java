package com.maidweapon.forge.item.sin;

import com.maidweapon.common.sin.SinType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import javax.annotation.Nullable;
import java.util.List;

public class GluttonyStomachItem extends SinBaseItem {
    public GluttonyStomachItem() { super(SinType.GLUTTONY, new Properties()); }

    @Override
    protected void appendSinTooltip(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.empty());
        tooltip.add(Component.literal("§f§l✦ 白骨之匣·无尽 ✦"));
        tooltip.add(Component.literal("§7匣子是空的。但你总觉得里面有什么东西在看着你。"));
        tooltip.add(Component.literal("§8但你能感觉到——这只是碎片。"));
        tooltip.add(Component.empty());
        tooltip.add(Component.literal("§f◆ 进食获得速度+急迫，额外恢复好感度"));
        tooltip.add(Component.literal("§c◇ 30%概率额外消耗耐久"));
    }
}
