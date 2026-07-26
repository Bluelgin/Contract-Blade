package com.maidweapon.common.system;

import com.maidweapon.common.data.MaidWeaponData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * ========================================
 * 好感度系统（通用层）
 * ========================================
 *
 * 直接映射 TLM 女仆好感度（0 ~ 384）。
 * 数值均可通过配置文件调整。
 */
public final class LoyaltySystem {

    public static final int MAX_FAVORABILITY = 384;

    private LoyaltySystem() {}

    /**
     * 获取好感度的等级描述（按比例，不依赖具体数值）
     */
    public static Component getFavorabilityTitle(int favorability) {
        int value = Math.max(0, Math.min(favorability, MAX_FAVORABILITY));
        if (value >= 320) {
            return Component.translatable("maid_weapon.favorability.inseparable")
                    .withStyle(ChatFormatting.LIGHT_PURPLE);
        }
        if (value >= 256) {
            return Component.translatable("maid_weapon.favorability.kindred")
                    .withStyle(ChatFormatting.AQUA);
        }
        if (value >= 192) {
            return Component.translatable("maid_weapon.favorability.trusted")
                    .withStyle(ChatFormatting.GREEN);
        }
        if (value >= 128) {
            return Component.translatable("maid_weapon.favorability.close")
                    .withStyle(ChatFormatting.YELLOW);
        }
        if (value >= 64) {
            return Component.translatable("maid_weapon.favorability.familiar")
                    .withStyle(ChatFormatting.WHITE);
        }
        return Component.translatable("maid_weapon.favorability.ordinary")
                .withStyle(ChatFormatting.GRAY);
    }

    /**
     * 判断是否应该显示低好感度警告
     */
    public static boolean isEarlyContract(MaidWeaponData data) {
        return data.getFavorability() < 64;
    }
}
