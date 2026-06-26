package com.maidweapon.forge.system;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 挑战状态追踪器 — 封装 CombatEventHandler 中散落的静态 Map。
 *
 * 每次击杀/受伤都会更新挑战状态，怪物死亡后清除。
 * 数据仅存在于内存中（Session 级），服务器重启后重置。
 */
public class ChallengeTracker {

    /** 当前挑战的最高 Tier */
    private static final Map<UUID, Integer> CHALLENGE_TIER = new HashMap<>();
    /** 挑战中是否含 Boss */
    private static final Map<UUID, Boolean> HAS_BOSS = new HashMap<>();
    /** 挑战中是否含末影龙 */
    private static final Map<UUID, Boolean> HAS_DRAGON = new HashMap<>();
    /** 挑战中是否含凋灵 */
    private static final Map<UUID, Boolean> HAS_WITHER = new HashMap<>();
    /** 挑战是否失败（玩家死亡） */
    private static final Map<UUID, Boolean> FAILED = new HashMap<>();

    /** 玩家上一 tick 的饱食度 */
    private static final Map<UUID, Integer> LAST_FOOD = new HashMap<>();
    /** 玩家上一 tick 的血量 */
    private static final Map<UUID, Float> LAST_HEALTH = new HashMap<>();

    // ==================== 挑战状态 ====================

    public static void setTier(UUID playerId, int tier) {
        Integer current = CHALLENGE_TIER.get(playerId);
        if (current == null || tier > current) {
            CHALLENGE_TIER.put(playerId, tier);
        }
    }

    public static Integer getTier(UUID playerId) {
        return CHALLENGE_TIER.get(playerId);
    }

    public static void setHasBoss(UUID playerId, boolean val) {
        if (val) HAS_BOSS.put(playerId, true);
    }

    public static boolean hasBoss(UUID playerId) {
        return HAS_BOSS.getOrDefault(playerId, false);
    }

    public static void setHasDragon(UUID playerId, boolean val) {
        if (val) HAS_DRAGON.put(playerId, true);
    }

    public static boolean hasDragon(UUID playerId) {
        return HAS_DRAGON.getOrDefault(playerId, false);
    }

    public static void setHasWither(UUID playerId, boolean val) {
        if (val) HAS_WITHER.put(playerId, true);
    }

    public static boolean hasWither(UUID playerId) {
        return HAS_WITHER.getOrDefault(playerId, false);
    }

    public static void setFailed(UUID playerId, boolean val) {
        FAILED.put(playerId, val);
    }

    public static boolean isFailed(UUID playerId) {
        return FAILED.getOrDefault(playerId, false);
    }

    /** 清除某个玩家的所有挑战状态 */
    public static void clear(UUID playerId) {
        CHALLENGE_TIER.remove(playerId);
        HAS_BOSS.remove(playerId);
        HAS_DRAGON.remove(playerId);
        HAS_WITHER.remove(playerId);
        FAILED.remove(playerId);
    }

    // ==================== 进食/回血追踪 ====================

    public static void setFoodLevel(UUID playerId, int level) {
        LAST_FOOD.put(playerId, level);
    }

    public static Integer getLastFoodLevel(UUID playerId) {
        return LAST_FOOD.get(playerId);
    }

    public static void setHealth(UUID playerId, float health) {
        LAST_HEALTH.put(playerId, health);
    }

    public static Float getLastHealth(UUID playerId) {
        return LAST_HEALTH.get(playerId);
    }
}
