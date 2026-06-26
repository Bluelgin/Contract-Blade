package com.maidweapon.forge.item.sin;

import com.maidweapon.common.sin.SinType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * ========================================
 * 七宗罪物品基类（Forge 端）
 * ========================================
 *
 * 所有七宗罪物品都继承此类。
 * 提供通用的物品行为和Tooltip显示。
 *
 * 贴图文件路径：
 *   assets/maid_weapon/textures/item/sin_xxx.png
 *   （由整合包作者/Mod作者自行绘制）
 *
 * 物品模型：
 *   assets/maid_weapon/models/item/sin_xxx.json
 */
public abstract class SinBaseItem extends Item {

    /** 此物品对应的罪恶类型 */
    private final SinType sinType;

    public SinBaseItem(SinType sinType, Properties properties) {
        super(properties
                .stacksTo(1)        // 不可堆叠
                .rarity(Rarity.EPIC) // 史诗稀有度
        );
        this.sinType = sinType;
    }

    /**
     * 获取此物品对应的罪恶类型
     */
    public SinType getSinType() {
        return sinType;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                 List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);

        tooltip.add(Component.empty());
        tooltip.add(Component.literal(sinType.getColor() + "§l═══ 七宗罪 ═══"));
        tooltip.add(Component.literal("§7将此物品放入铁砧左侧，"));
        tooltip.add(Component.literal("§7女仆武器放入右侧，"));
        tooltip.add(Component.literal("§7消耗3级经验即可嵌入罪恶。"));

        // 显示具体效果（子类实现）
        appendSinTooltip(stack, level, tooltip, flag);

        tooltip.add(Component.empty());
        tooltip.add(Component.literal("§8「以罪为刃，以恶为盾」"));
    }

    /**
     * 子类实现：显示具体的Buff和Debuff效果
     */
    protected abstract void appendSinTooltip(ItemStack stack, @Nullable Level level,
                                              List<Component> tooltip, TooltipFlag flag);

    @Override
    public boolean isFoil(ItemStack stack) {
        return true; // 罪恶物品始终发光
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.literal(sinType.getColor() + sinType.getChineseName());
    }
}