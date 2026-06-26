package com.maidweapon.forge.item.sin;

import com.maidweapon.common.sin.SinType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import javax.annotation.Nullable;
import java.util.List;

public class EnvyPupilItem extends SinBaseItem {
    public EnvyPupilItem() { super(SinType.ENVY, new Properties()); }

    @Override
    protected void appendSinTooltip(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.empty());
        tooltip.add(Component.literal("§2§l✦ 蛇纹书页·怨叹 ✦"));
        tooltip.add(Component.literal("§7书页上的文字每隔一段时间就会变动，"));
        tooltip.add(Component.literal("§7像是有谁还在写着它。"));
        tooltip.add(Component.literal("§8但你能感觉到——这只是碎片。"));
        tooltip.add(Component.empty());
        tooltip.add(Component.literal("§2◆ 攻击高Tier怪物 ×2.0"));
        tooltip.add(Component.literal("§c◇ 攻击低Tier怪物 ×0.2"));
    }
}
