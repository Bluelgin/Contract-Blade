package com.maidweapon.common.system;

import com.maidweapon.common.data.MaidWeaponData;

import java.util.HashMap;
import java.util.Map;

/**
 * ========================================
 * 怪物强度分级注册表（通用层）
 * ========================================
 *
 * 将各种怪物映射到对应的强度Tier。
 * 使用字符串（实体注册名）作为键，方便跨平台兼容。
 *
 * 其他Mod可以通过 ModCompatManager 注册额外的怪物Tier。
 */
public final class MonsterTierRegistry {

    /** 怪物注册名 → Tier 的映射 */
    private static final Map<String, Integer> TIER_MAP = new HashMap<>();

    /** Boss 级怪物注册名集合 */
    private static final Map<String, Boolean> BOSS_MAP = new HashMap<>();

    static {
        // ============ TIER 1: 普通怪物 ============
        registerTier("minecraft:zombie", MaidWeaponData.TIER_1);
        registerTier("minecraft:zombie_villager", MaidWeaponData.TIER_1);
        registerTier("minecraft:husk", MaidWeaponData.TIER_1);
        registerTier("minecraft:drowned", MaidWeaponData.TIER_1);
        registerTier("minecraft:skeleton", MaidWeaponData.TIER_1);
        registerTier("minecraft:stray", MaidWeaponData.TIER_1);
        registerTier("minecraft:wither_skeleton", MaidWeaponData.TIER_1);
        registerTier("minecraft:spider", MaidWeaponData.TIER_1);
        registerTier("minecraft:cave_spider", MaidWeaponData.TIER_1);
        registerTier("minecraft:creeper", MaidWeaponData.TIER_1);
        registerTier("minecraft:enderman", MaidWeaponData.TIER_1);
        registerTier("minecraft:blaze", MaidWeaponData.TIER_1);
        registerTier("minecraft:ghast", MaidWeaponData.TIER_1);
        registerTier("minecraft:magma_cube", MaidWeaponData.TIER_1);
        registerTier("minecraft:slime", MaidWeaponData.TIER_1);
        registerTier("minecraft:phantom", MaidWeaponData.TIER_1);
        registerTier("minecraft:zombie_horse", MaidWeaponData.TIER_1);
        registerTier("minecraft:silverfish", MaidWeaponData.TIER_1);

        // ============ TIER 2: 较强怪物 ============
        registerTier("minecraft:pillager", MaidWeaponData.TIER_2);
        registerTier("minecraft:vindicator", MaidWeaponData.TIER_2);
        registerTier("minecraft:evoker", MaidWeaponData.TIER_2);
        registerTier("minecraft:vex", MaidWeaponData.TIER_2);
        registerTier("minecraft:witch", MaidWeaponData.TIER_2);
        registerTier("minecraft:ravager", MaidWeaponData.TIER_2);
        registerTier("minecraft:hoglin", MaidWeaponData.TIER_2);
        registerTier("minecraft:piglin_brute", MaidWeaponData.TIER_2);
        registerTier("minecraft:wolf", MaidWeaponData.TIER_2); // 敌对状态的狼

        // ============ TIER 3: 精英怪物 ============
        registerTier("minecraft:shulker", MaidWeaponData.TIER_3);
        registerTier("minecraft:guardian", MaidWeaponData.TIER_3);
        registerTier("minecraft:illusioner", MaidWeaponData.TIER_3);

        // ============ TIER 4: 准Boss ============
        registerTier("minecraft:elder_guardian", MaidWeaponData.TIER_4);
        registerTier("minecraft:warden", MaidWeaponData.TIER_4);

        // ============ TIER 5: Boss ============
        registerBoss("minecraft:ender_dragon", true);
        registerBoss("minecraft:wither", true);
    }

    /**
     * 注册怪物的Tier等级
     * @param entityId 怪物注册名（如 "minecraft:zombie"）
     * @param tier 强度等级
     */
    public static void registerTier(String entityId, int tier) {
        TIER_MAP.put(entityId, Math.max(0, Math.min(tier, MaidWeaponData.TIER_5)));
    }

    /**
     * 注册Boss怪物
     * @param entityId 怪物注册名
     * @param isBoss 是否为Boss
     */
    public static void registerBoss(String entityId, boolean isBoss) {
        BOSS_MAP.put(entityId, isBoss);
        TIER_MAP.put(entityId, MaidWeaponData.TIER_5);
    }

    /**
     * 获取怪物的Tier等级
     * @param entityId 怪物注册名
     * @return Tier等级，未注册的怪物返回 TIER_NONE
     */
    public static int getTier(String entityId) {
        return TIER_MAP.getOrDefault(entityId, MaidWeaponData.TIER_NONE);
    }

    /**
     * 获取怪物的Tier等级（通过最大血量估算）
     * 适用于未在注册表中注册的怪物（来自其他Mod的怪物）
     * @param maxHealth 怪物最大血量
     * @return 估算的Tier等级
     */
    public static int estimateTierFromHealth(float maxHealth) {
        if (maxHealth >= 200) return MaidWeaponData.TIER_5;   // Boss级别
        if (maxHealth >= 100) return MaidWeaponData.TIER_4;   // 准Boss
        if (maxHealth >= 50)  return MaidWeaponData.TIER_3;   // 精英
        if (maxHealth >= 20)  return MaidWeaponData.TIER_2;   // 较强
        if (maxHealth >= 10)  return MaidWeaponData.TIER_1;   // 普通
        return MaidWeaponData.TIER_NONE;                       // 非战斗
    }

    /**
     * 判断怪物是否为Boss
     * @param entityId 怪物注册名
     * @return 是否为Boss
     */
    public static boolean isBoss(String entityId) {
        return BOSS_MAP.getOrDefault(entityId, false);
    }

    /**
     * 判断怪物是否为Boss（通过最大血量估算）
     * @param maxHealth 怪物最大血量
     * @return 是否为Boss
     */
    public static boolean isBossFromHealth(float maxHealth) {
        return maxHealth >= 200;
    }

    /**
     * 获取Tier的显示名称
     */
    public static String getTierName(int tier) {
        return switch (tier) {
            case MaidWeaponData.TIER_1 -> "§a普通";
            case MaidWeaponData.TIER_2 -> "§e较强";
            case MaidWeaponData.TIER_3 -> "§6精英";
            case MaidWeaponData.TIER_4 -> "§c准Boss";
            case MaidWeaponData.TIER_5 -> "§4Boss";
            default -> "§7无";
        };
    }

    private MonsterTierRegistry() {}
}