package com.maidweapon.forge.item.sin;

import com.maidweapon.common.sin.SinSlotManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * ========================================
 * 忏悔之泪（Tear of Repentance）- Forge 端
 * ========================================
 *
 * 用于从女仆武器中取出（移除）罪恶物品。
 *
 * 使用方式：
 *   铁砧左侧放入「嵌入罪恶的武器」，右侧放入「忏悔之泪」
 *   → 消耗5级经验 → 武器归还（不含罪恶）+ 罪恶物品
 *
 * 充能方式：
 *   铁砧左侧放入「忏悔之泪」，右侧放入「充能物品（默认：下界之星）」
 *   → 恢复使用次数
 *
 * 配置可自定义：
 *   - 最大使用次数（默认7次）
 *   - 充能物品ID（默认下界之星）
 *   - 每次充能恢复次数（默认1次）
 *
 * 贴图文件：assets/maid_weapon/textures/item/tear_of_repentance.png
 * 物品模型：assets/maid_weapon/models/item/tear_of_repentance.json
 */
public class TearOfRepentanceItem extends Item {

    /** 默认最大使用次数 */
    public static final int DEFAULT_MAX_CHARGES = 7;

    public TearOfRepentanceItem() {
        super(new Properties()
                .stacksTo(1)
                .rarity(Rarity.RARE)
        );
    }

    /**
     * 获取剩余使用次数
     */
    public static int getRemainingCharges(ItemStack stack) {
        if (!stack.hasTag()) return DEFAULT_MAX_CHARGES;
        int used = stack.getTag().getInt("ChargesUsed");
        return Math.max(0, DEFAULT_MAX_CHARGES - used);
    }

    /**
     * 获取已使用次数
     */
    public static int getUsedCharges(ItemStack stack) {
        if (!stack.hasTag()) return 0;
        return stack.getTag().getInt("ChargesUsed");
    }

    /**
     * 消耗一次使用次数
     * @return 是否消耗成功
     */
    public static boolean consumeCharge(ItemStack stack) {
        if (getRemainingCharges(stack) <= 0) return false;
        int used = getUsedCharges(stack);
        stack.getOrCreateTag().putInt("ChargesUsed", used + 1);
        return true;
    }

    /**
     * 充能（恢复使用次数）
     * @param amount 恢复次数
     */
    public static void recharge(ItemStack stack, int amount) {
        int used = getUsedCharges(stack);
        int newUsed = Math.max(0, used - amount);
        stack.getOrCreateTag().putInt("ChargesUsed", newUsed);
    }

    /**
     * 检查是否还有使用次数
     */
    public static boolean hasCharges(ItemStack stack) {
        return getRemainingCharges(stack) > 0;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                 List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);

        tooltip.add(Component.empty());
        tooltip.add(Component.literal("§b§l═══ 忏悔之泪 ═══"));
        tooltip.add(Component.empty());

        int remaining = getRemainingCharges(stack);
        ChatFormatting color = remaining > 3 ? ChatFormatting.GREEN :
                              remaining > 0 ? ChatFormatting.YELLOW : ChatFormatting.RED;

        tooltip.add(Component.literal(
                String.format("§e剩余使用次数: %s%d§7/%d", color, remaining, DEFAULT_MAX_CHARGES)
        ));

        tooltip.add(Component.empty());
        tooltip.add(Component.literal("§7嵌入时使用：铁砧左侧放武器，"));
        tooltip.add(Component.literal("§7右侧放此物品，消耗3级经验"));
        tooltip.add(Component.literal("§7可从武器中取出罪恶物品。"));

        tooltip.add(Component.empty());
        tooltip.add(Component.literal("§7充能：铁砧左侧放此物品，"));
        tooltip.add(Component.literal("§7右侧放下界之星，消耗1级经验"));

        if (remaining <= 0) {
            tooltip.add(Component.empty());
            tooltip.add(Component.literal("§c⚠ 使用次数已耗尽，需要充能！"));
        }

        tooltip.add(Component.empty());
        tooltip.add(Component.literal("§8「罪恶可赎，救赎之路永存」"));
        tooltip.add(Component.empty());
        tooltip.add(Component.literal("§7据说，这颗泪的主人还在封印里"));
        tooltip.add(Component.literal("§7等着一个人去找她。"));
        tooltip.add(Component.literal("§8——「明……你在哪。」"));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return hasCharges(stack);
    }

    @Override
    public Component getName(ItemStack stack) {
        int remaining = getRemainingCharges(stack);
        if (remaining <= 0) {
            return Component.literal("§7忏悔之泪 §8(已耗尽)");
        }
        return Component.literal("§b忏悔之泪");
    }
}