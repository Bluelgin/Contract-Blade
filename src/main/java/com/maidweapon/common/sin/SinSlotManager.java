package com.maidweapon.common.sin;

import java.util.ArrayList;
import java.util.List;

/**
 * ========================================
 * 罪恶槽位管理器（通用层）
 * ========================================
 *
 * 管理武器上的罪恶槽位。
 *
 * 规则：
 *   - 默认情况下，武器最多嵌入 1 个罪恶
 *   - 如果嵌入的是「傲慢之冠」，则可以继续嵌入其他罪恶（最多7个）
 *   - 嵌入傲慢后，所有罪恶的Debuff同时生效
 *   - 取出罪恶需要使用「忏悔之泪」
 *
 * 逻辑：
 *   canEmbed(weaponSins, newSin) → 是否可以嵌入
 *   embed(weaponSins, newSin) → 嵌入罪恶
 *   remove(weaponSins, sinToRemove) → 移除罪恶
 *   hasPride(weaponSins) → 是否嵌入了傲慢
 */
public final class SinSlotManager {

    /** 默认最大槽位数（没有傲慢时） */
    public static final int DEFAULT_MAX_SLOTS = 1;

    /** 傲慢后的最大槽位数（全部7个） */
    public static final int PRIDE_MAX_SLOTS = 7;

    private SinSlotManager() {}

    /**
     * 检查是否可以嵌入新的罪恶
     * @param currentSins 当前已嵌入的罪恶列表
     * @param newSin 要嵌入的新罪恶
     * @return 是否可以嵌入
     */
    public static boolean canEmbed(List<SinType> currentSins, SinType newSin) {
        // 不能重复嵌入同一种罪恶
        if (currentSins.contains(newSin)) return false;

        // 如果已有傲慢，可以嵌入所有罪恶
        if (hasPride(currentSins)) {
            return currentSins.size() < PRIDE_MAX_SLOTS;
        }

        // 如果要嵌入的是傲慢，检查是否已有其他罪恶
        // 有其他罪恶时也可以嵌入傲慢（傲慢会解锁全部槽位）
        if (newSin.isPride()) {
            return true; // 傲慢总是可以嵌入
        }

        // 没有傲慢时，最多只能有1个罪恶
        return currentSins.isEmpty();
    }

    /**
     * 嵌入罪恶
     * @param currentSins 当前已嵌入的罪恶列表
     * @param newSin 要嵌入的新罪恶
     * @return 嵌入后的新列表（如果不能嵌入则返回原列表）
     */
    public static List<SinType> embed(List<SinType> currentSins, SinType newSin) {
        if (!canEmbed(currentSins, newSin)) return currentSins;

        List<SinType> result = new ArrayList<>(currentSins);
        result.add(newSin);
        return result;
    }

    /**
     * 移除罪恶
     * @param currentSins 当前已嵌入的罪恶列表
     * @param sinToRemove 要移除的罪恶
     * @return 移除后的新列表
     */
    public static List<SinType> remove(List<SinType> currentSins, SinType sinToRemove) {
        List<SinType> result = new ArrayList<>(currentSins);
        result.remove(sinToRemove);

        // 如果移除了傲慢，且还有其他罪恶，需要检查是否超限
        // 没有傲慢时只能保留1个，保留最先嵌入的那个
        if (!sinToRemove.isPride() || !hasPride(result)) {
            // 检查移除后是否超限（没有傲慢只能1个）
            if (!hasPride(result) && result.size() > DEFAULT_MAX_SLOTS) {
                // 只保留第一个
                result = new ArrayList<>(result.subList(0, DEFAULT_MAX_SLOTS));
            }
        }

        return result;
    }

    /**
     * 移除傲慢及所有其他罪恶
     * @return 空列表
     */
    public static List<SinType> removeAll(List<SinType> currentSins) {
        return new ArrayList<>();
    }

    /**
     * 是否嵌入了傲慢
     */
    public static boolean hasPride(List<SinType> sins) {
        return sins.contains(SinType.PRIDE);
    }

    /**
     * 是否嵌入了指定罪恶
     */
    public static boolean hasSin(List<SinType> sins, SinType type) {
        return sins.contains(type);
    }

    /**
     * 获取当前槽位数
     */
    public static int getSlotCount(List<SinType> sins) {
        return sins.size();
    }

    /**
     * 获取最大槽位数
     */
    public static int getMaxSlots(List<SinType> sins) {
        return hasPride(sins) ? PRIDE_MAX_SLOTS : DEFAULT_MAX_SLOTS;
    }

    /**
     * 将罪恶列表转为NBT存储的字符串（逗号分隔）
     */
    public static String sinsToString(List<SinType> sins) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sins.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(sins.get(i).getId());
        }
        return sb.toString();
    }

    /**
     * 从NBT字符串恢复罪恶列表
     */
    public static List<SinType> sinsFromString(String str) {
        List<SinType> result = new ArrayList<>();
        if (str == null || str.isEmpty()) return result;

        for (String id : str.split(",")) {
            SinType type = SinType.fromId(id.trim());
            if (type != null) {
                result.add(type);
            }
        }
        return result;
    }
}