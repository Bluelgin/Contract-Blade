package com.maidweapon.port.domain.mastery;


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
        registerTier("minecraft:zombie", 1);
        registerTier("minecraft:zombie_villager", 1);
        registerTier("minecraft:husk", 1);
        registerTier("minecraft:drowned", 1);
        registerTier("minecraft:skeleton", 1);
        registerTier("minecraft:stray", 1);
        registerTier("minecraft:wither_skeleton", 1);
        registerTier("minecraft:spider", 1);
        registerTier("minecraft:cave_spider", 1);
        registerTier("minecraft:creeper", 1);
        registerTier("minecraft:enderman", 1);
        registerTier("minecraft:blaze", 1);
        registerTier("minecraft:ghast", 1);
        registerTier("minecraft:magma_cube", 1);
        registerTier("minecraft:slime", 1);
        registerTier("minecraft:phantom", 1);
        registerTier("minecraft:zombie_horse", 1);
        registerTier("minecraft:silverfish", 1);

        // ============ TIER 2: 较强怪物 ============
        registerTier("minecraft:pillager", 2);
        registerTier("minecraft:vindicator", 2);
        registerTier("minecraft:evoker", 2);
        registerTier("minecraft:vex", 2);
        registerTier("minecraft:witch", 2);
        registerTier("minecraft:ravager", 2);
        registerTier("minecraft:hoglin", 2);
        registerTier("minecraft:piglin_brute", 2);
        registerTier("minecraft:wolf", 2); // 敌对状态的狼

        // ============ TIER 3: 精英怪物 ============
        registerTier("minecraft:shulker", 3);
        registerTier("minecraft:guardian", 3);
        registerTier("minecraft:illusioner", 3);

        // ============ TIER 4: 准Boss ============
        registerTier("minecraft:elder_guardian", 4);
        registerTier("minecraft:warden", 4);

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
        TIER_MAP.put(entityId, Math.max(0, Math.min(tier, 5)));
    }

    /**
     * 注册Boss怪物
     * @param entityId 怪物注册名
     * @param isBoss 是否为Boss
     */
    public static void registerBoss(String entityId, boolean isBoss) {
        BOSS_MAP.put(entityId, isBoss);
        TIER_MAP.put(entityId, 5);
    }

    /**
     * 获取怪物的Tier等级
     * @param entityId 怪物注册名
     * @return Tier等级，未注册的怪物返回 TIER_NONE
     */
    public static int getTier(String entityId) {
        return TIER_MAP.getOrDefault(entityId, 0);
    }

    /**
     * 获取怪物的Tier等级（通过最大血量估算）
     * 适用于未在注册表中注册的怪物（来自其他Mod的怪物）
     * @param maxHealth 怪物最大血量
     * @return 估算的Tier等级
     */
    public static int estimateTierFromHealth(float maxHealth) {
        if (maxHealth >= 200) return 5;   // Boss级别
        if (maxHealth >= 100) return 4;   // 准Boss
        if (maxHealth >= 50)  return 3;   // 精英
        if (maxHealth >= 20)  return 2;   // 较强
        if (maxHealth >= 10)  return 1;   // 普通
        return 0;                       // 非战斗
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
            case 1 -> "§a普通";
            case 2 -> "§e较强";
            case 3 -> "§6精英";
            case 4 -> "§c准Boss";
            case 5 -> "§4Boss";
            default -> "§7无";
        };
    }

    private MonsterTierRegistry() {}
}