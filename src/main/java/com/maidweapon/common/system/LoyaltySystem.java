package com.maidweapon.common.system;

import com.maidweapon.common.MaidWeaponConfig;
import com.maidweapon.common.data.MaidWeaponData;

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
     * 处理击杀后的好感度变化
     * 消耗量 = 怪物最大血量 / killConsumptionDivisor
     */
    public static void onKill(MaidWeaponData data, float monsterMaxHealth) {
        int divisor = MaidWeaponConfig.KILL_CONSUMPTION_DIVISOR.get();
        int loss = Math.max(1, (int)(monsterMaxHealth / divisor));
        data.addFavorability(-loss);
    }

    /**
     * 处理自然恢复（每个游戏日调用一次）
     */
    public static void naturalRecovery(MaidWeaponData data) {
        data.addFavorability(MaidWeaponConfig.DAILY_RECOVERY.get());
    }

    /** 带倍率的自然恢复（神树附近 ×3） */
    public static void naturalRecovery(MaidWeaponData data, int multiplier) {
        data.addFavorability(MaidWeaponConfig.DAILY_RECOVERY.get() * multiplier);
    }

    /**
     * 进食恢复好感度
     */
    public static void onEat(MaidWeaponData data, float foodSaturation) {
        double multiplier = MaidWeaponConfig.FOOD_RECOVERY_MULTIPLIER.get();
        int gain = (int) (foodSaturation * multiplier);
        if (gain > 0) {
            data.addFavorability(gain);
        }
    }

    /**
     * 恢复生命时恢复好感度
     */
    public static void onHeal(MaidWeaponData data, float healedAmount) {
        double multiplier = MaidWeaponConfig.HEAL_RECOVERY_MULTIPLIER.get();
        int gain = (int) (healedAmount * multiplier);
        if (gain > 0) {
            data.addFavorability(gain);
        }
    }

    /**
     * 获取好感度的等级描述（按比例，不依赖具体数值）
     */
    public static String getFavorabilityTitle(int favorability) {
        float p = (float) favorability / MAX_FAVORABILITY;
        if (p >= 0.78f) return "§a生死相依";
        if (p >= 0.52f) return "§a忠心耿耿";
        if (p >= 0.26f) return "§e相处融洽";
        if (p >= 0.13f) return "§6略有不满";
        if (p >= 0.03f) return "§c心生怨怼";
        return "§4濒临叛逃";
    }

    /**
     * 判断是否应该显示低好感度警告
     */
    public static boolean shouldWarnLowFavorability(MaidWeaponData data) {
        return data.getFavorability() < 50;
    }
}
