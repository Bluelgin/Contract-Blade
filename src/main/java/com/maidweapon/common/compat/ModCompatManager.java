package com.maidweapon.common.compat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ========================================
 * Mod 兼容管理器（通用层）
 * ========================================
 *
 * 提供一个统一的接口，允许其他Mod与MaidWeapon进行联动。
 *
 * 使用方式（其他Mod的开发者可以这样做）：
 *   1. 注册怪物Tier：ModCompatManager.registerMonsterTier("ic2:entity_name", 3);
 *   2. 注册升级条件：ModCompatManager.registerUpgradeCondition(...)
 *   3. 注册自定义事件回调：ModCompatManager.addWeaponEventListener(...)
 *
 * 设计原则：
 *   - 所有注册方法都是静态的，无需实例化
 *   - 使用字符串标识符，避免硬编码类依赖
 *   - 通用层不依赖任何Forge/Fabric API
 */
public final class ModCompatManager {

    // ==================== 已注册的外部怪物Tier ====================
    private static final Map<String, Integer> EXTERNAL_TIERS = new HashMap<>();

    // ==================== 已注册的外部Boss ====================
    private static final Map<String, Boolean> EXTERNAL_BOSSES = new HashMap<>();

    // ==================== 武器事件监听器 ====================
    private static final List<WeaponEventListener> EVENT_LISTENERS = new ArrayList<>();

    // ==================== 外部注册的升级条件 ====================
    private static final Map<Integer, UpgradeCondition> CUSTOM_UPGRADE_CONDITIONS = new HashMap<>();

    // ==================== 怪物Tier注册 ====================

    /**
     * 注册外部Mod的怪物Tier
     *
     * 示例：
     *   // 冰与火之歌的龙
     *   ModCompatManager.registerMonsterTier("iceandfire:fire_dragon", 5);
     *
     *   // 神话生物
     *   ModCompatManager.registerMonsterTier("mowziesmobs:ferrous_wroughtnaut", 4);
     *
     * @param entityId 怪物的完整注册名（modid:entity_name）
     * @param tier 强度等级（0~5）
     */
    public static void registerMonsterTier(String entityId, int tier) {
        EXTERNAL_TIERS.put(entityId, Math.max(0, Math.min(tier, 5)));
    }

    /**
     * 批量注册怪物Tier
     * @param tiers Map<entityId, tier>
     */
    public static void registerMonsterTiers(Map<String, Integer> tiers) {
        for (Map.Entry<String, Integer> entry : tiers.entrySet()) {
            registerMonsterTier(entry.getKey(), entry.getValue());
        }
    }

    /**
     * 注册外部Mod的Boss怪物
     * @param entityId 怪物的完整注册名
     * @param isBoss 是否为Boss
     */
    public static void registerBoss(String entityId, boolean isBoss) {
        EXTERNAL_BOSSES.put(entityId, isBoss);
        if (isBoss) {
            EXTERNAL_TIERS.put(entityId, 5);
        }
    }

    /**
     * 获取所有已注册的外部怪物Tier
     */
    public static Map<String, Integer> getExternalTiers() {
        return new HashMap<>(EXTERNAL_TIERS);
    }

    /**
     * 获取所有已注册的外部Boss
     */
    public static Map<String, Boolean> getExternalBosses() {
        return new HashMap<>(EXTERNAL_BOSSES);
    }

    /**
     * 查询外部怪物的Tier（包含已注册的外部怪物）
     * 优先级：外部Boss > 外部Tier > 返回-1（未找到）
     * @param entityId 怪物注册名
     * @return Tier等级，-1表示未注册
     */
    public static int getExternalTier(String entityId) {
        // 先检查是否是Boss
        if (EXTERNAL_BOSSES.getOrDefault(entityId, false)) {
            return 5;
        }
        // 再检查Tier映射
        return EXTERNAL_TIERS.getOrDefault(entityId, -1);
    }

    // ==================== 升级条件注册 ====================

    /**
     * 注册自定义升级条件
     *
     * 示例：
     *   // Lv.9 的升级条件：击杀100只怪物
     *   ModCompatManager.registerUpgradeCondition(9, (data, context) -> {
     *       return data.getTotalKills() >= 100;
     *   });
     *
     * @param level 目标等级
     * @param condition 升级条件
     */
    public static void registerUpgradeCondition(int level, UpgradeCondition condition) {
        CUSTOM_UPGRADE_CONDITIONS.put(level, condition);
    }

    /**
     * 获取自定义升级条件
     * @param level 目标等级
     * @return 升级条件，如果没有自定义条件则返回 null
     */
    public static UpgradeCondition getUpgradeCondition(int level) {
        return CUSTOM_UPGRADE_CONDITIONS.get(level);
    }

    // ==================== 事件监听器 ====================

    /**
     * 添加武器事件监听器
     * @param listener 事件监听器
     */
    public static void addWeaponEventListener(WeaponEventListener listener) {
        EVENT_LISTENERS.add(listener);
    }

    /**
     * 获取所有事件监听器
     */
    public static List<WeaponEventListener> getEventListeners() {
        return new ArrayList<>(EVENT_LISTENERS);
    }

    /**
     * 通知所有监听器：武器升级了
     */
    public static void notifyUpgrade(String weaponId, int oldLevel, int newLevel) {
        for (WeaponEventListener listener : EVENT_LISTENERS) {
            listener.onWeaponUpgrade(weaponId, oldLevel, newLevel);
        }
    }

    /**
     * 通知所有监听器：怪物被击杀了
     */
    public static void notifyMonsterKilled(String weaponId, String monsterId, int tier, boolean isBoss) {
        for (WeaponEventListener listener : EVENT_LISTENERS) {
            listener.onMonsterKilled(weaponId, monsterId, tier, isBoss);
        }
    }

    /**
     * 通知所有监听器：挑战失败（玩家死亡）
     */
    public static void notifyChallengeFailed(String weaponId, String monsterId) {
        for (WeaponEventListener listener : EVENT_LISTENERS) {
            listener.onChallengeFailed(weaponId, monsterId);
        }
    }

    // ==================== 接口定义 ====================

    /**
     * 升级条件接口
     * 其他Mod可以实现此接口来定义自定义升级条件
     */
    @FunctionalInterface
    public interface UpgradeCondition {
        /**
         * 检查是否满足升级条件
         * @param data 女仆武器数据
         * @param context 额外上下文信息（可以为null）
         * @return 是否满足条件
         */
        boolean check(com.maidweapon.common.data.MaidWeaponData data, Object context);
    }

    /**
     * 武器事件监听器接口
     * 其他Mod可以实现此接口来监听武器的各种事件
     */
    public interface WeaponEventListener {
        /** 武器升级时触发 */
        default void onWeaponUpgrade(String weaponId, int oldLevel, int newLevel) {}

        /** 击杀怪物时触发 */
        default void onMonsterKilled(String weaponId, String monsterId, int tier, boolean isBoss) {}

        /** 挑战失败时触发 */
        default void onChallengeFailed(String weaponId, String monsterId) {}

        /** 挑战开始时触发 */
        default void onChallengeStarted(String weaponId, String monsterId) {}
    }

    private ModCompatManager() {}
}