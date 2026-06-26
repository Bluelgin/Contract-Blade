package com.maidweapon.common.data;

import com.maidweapon.common.MaidWeaponConfig;
import com.maidweapon.common.sin.SinType;
import com.maidweapon.common.sin.SinSlotManager;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * ========================================
 * 女仆武器数据（通用层）
 * ========================================
 *
 * 成长系统核心机制：
 *   使用女仆武器攻击怪物后，如果在该怪物死亡前玩家没有死亡，
 *   则根据怪物的强度等级解锁对应的武器等级。
 *
 * 怪物强度分级（Tier）：
 *   TIER_0 (0) → 无战斗力生物（鸡、猪、牛等）
 *   TIER_1 (1) → 普通怪物（僵尸、骷髅、蜘蛛等）
 *   TIER_2 (2) → 较强怪物（灾厄村民、洞穴蜘蛛等）
 *   TIER_3 (3) → 精英怪物（潜影贝、守卫者等）
 *   TIER_4 (4) → 准Boss（远古守卫者、监守者等）
 *   TIER_5 (5) → Boss（末影龙、凋灵）
 *
 * 武器等级：
 *   Lv.1 → 初始等级
 *   Lv.2 → 击杀任意 TIER_1 怪物（无死亡）
 *   Lv.3 → 击杀 TIER_2 怪物（无死亡）
 *   Lv.4 → 击杀 TIER_3 怪物（无死亡）
 *   Lv.5 → 击杀 TIER_4 怪物（无死亡）
 *   Lv.6 → 击杀末影龙（无死亡）
 *   Lv.7 → 击杀凋灵（无死亡）
 *   Lv.8 → 击杀末影龙+凋灵各3次（无死亡）
 *   Lv.9 → 特殊挑战（未来扩展）
 *   Lv.10 → 完全觉醒（未来扩展）
 */
public class MaidWeaponData {

    // ==================== 基础信息 ====================

    /** 女仆名称 */
    private String maidName = "Unknown Maid";

    /** 当前等级（1 ~ MAX_LEVEL） */
    private int level = 1;

    /** 好感度（0 ~ 384，对应 TLM 好感度） */
    private int favorability = 0;

    // ==================== 战斗统计 ====================

    /** 总击杀数 */
    private int totalKills = 0;

    /** 已解锁的最高怪物Tier */
    private int unlockedTier = 0;

    /** 末影龙击杀数（无死亡） */
    private int enderDragonKills = 0;

    /** 凋灵击杀数（无死亡） */
    private int witherKills = 0;

    // ==================== 罪恶系统 ====================

    /** 已嵌入的罪恶列表 */
    private List<SinType> embeddedSins = new ArrayList<>();

    // ==================== 常量 ====================

    /** 最大等级 */
    public static final int MAX_LEVEL = 10;

    /** 最大好感度（TLM 上限 384） */
    public static final int MAX_FAVORABILITY = 384;

    /** 最小好感度 */
    public static final int MIN_FAVORABILITY = 0;

    // ==================== 怪物强度分级 ====================

    public static final int TIER_NONE = 0;   // 非战斗生物
    public static final int TIER_1 = 1;      // 普通怪物
    public static final int TIER_2 = 2;      // 较强怪物
    public static final int TIER_3 = 3;      // 精英怪物
    public static final int TIER_4 = 4;      // 准Boss
    public static final int TIER_5 = 5;      // Boss

    // ==================== 等级 → 所需Tier映射 ====================

    /**
     * 升到每一级所需要的怪物Tier
     * 索引 0 = Lv.1→Lv.2 的需求，以此类推
     */
    public static final int[] TIER_REQUIREMENT = {
        TIER_1,   // Lv.1 → Lv.2: 击杀普通怪物
        TIER_2,   // Lv.2 → Lv.3: 击杀较强怪物
        TIER_3,   // Lv.3 → Lv.4: 击杀精英怪物
        TIER_4,   // Lv.4 → Lv.5: 击杀准Boss
        TIER_5,   // Lv.5 → Lv.6: 击杀末影龙
        TIER_5,   // Lv.6 → Lv.7: 击杀凋灵（特殊Boss）
        TIER_5,   // Lv.7 → Lv.8: 击杀Boss多次
        TIER_5,   // Lv.8 → Lv.9: 特殊挑战（待定）
        TIER_5    // Lv.9 → Lv.10: 完全觉醒（待定）
    };

    // ==================== 构造器 ====================

    public MaidWeaponData() {}

    public MaidWeaponData(String maidName) {
        this.maidName = maidName;
    }

    // ==================== 等级 ====================

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = Math.max(1, Math.min(level, MAX_LEVEL));
    }

    /**
     * 尝试升级武器
     * @param defeatedTier 被击杀怪物的Tier
     * @return 是否成功升级
     */
    public boolean tryUpgrade(int defeatedTier, boolean isEnderDragon, boolean isWither) {
        if (level >= MAX_LEVEL) return false;

        int requiredTier = TIER_REQUIREMENT[level - 1];

        // 基本条件：击杀怪物的Tier必须达到要求
        if (defeatedTier < requiredTier) return false;

        // 特殊条件检查（Lv.7+）
        if (level >= 5) {
            // 记录Boss击杀
            if (isEnderDragon) enderDragonKills++;
            if (isWither) witherKills++;

            // Lv.5 → Lv.6: 必须击杀末影龙
            if (level == 5 && !isEnderDragon) return false;

            // Lv.6 → Lv.7: 必须击杀凋灵
            if (level == 6 && !isWither) return false;

            // Lv.7 → Lv.8: 末影龙+凋灵各击杀3次
            if (level == 7) {
                if (enderDragonKills < 3 || witherKills < 3) return false;
            }

            // Lv.8 → Lv.9: 需要击杀Boss 5次
            if (level == 8 && (enderDragonKills + witherKills) < 5) return false;

            // Lv.9 → Lv.10: 需要击杀Boss 10次
            if (level == 9 && (enderDragonKills + witherKills) < 10) return false;
        }

        // 升级成功
        level++;
        if (defeatedTier > unlockedTier) {
            unlockedTier = defeatedTier;
        }
        return true;
    }

    /**
     * 获取升到下一级的提示文本
     */
    public String getNextUpgradeHint() {
        if (level >= MAX_LEVEL) return "已达到最大等级";

        if (level == 5) return "需要无死亡击杀末影龙";
        if (level == 6) return "需要无死亡击杀凋灵";
        if (level == 7) return "需要无死亡击杀末影龙和凋灵各3次";
        if (level == 8) return "§7(未开放)";
        if (level == 9) return "§7(未开放)";

        int requiredTier = TIER_REQUIREMENT[level - 1];
        return "需要无死亡击杀 TIER " + requiredTier + " 级怪物";
    }

    // ==================== 好感度 ====================

    public int getFavorability() {
        return favorability;
    }

    public void setFavorability(int favorability) {
        this.favorability = Math.max(MIN_FAVORABILITY, Math.min(favorability, MAX_FAVORABILITY));
    }

    public void addFavorability(int amount) {
        setFavorability(this.favorability + amount);
    }

    public void reduceFavorability(int amount) {
        setFavorability(this.favorability - amount);
    }

    // ==================== 战斗统计 ====================

    public String getMaidName() {
        return maidName;
    }

    public void setMaidName(String maidName) {
        this.maidName = maidName;
    }

    public int getTotalKills() {
        return totalKills;
    }

    public void setTotalKills(int totalKills) {
        this.totalKills = totalKills;
    }

    public void addKill() {
        this.totalKills++;
    }

    public int getUnlockedTier() {
        return unlockedTier;
    }

    public void setUnlockedTier(int unlockedTier) {
        this.unlockedTier = Math.max(0, Math.min(unlockedTier, TIER_5));
    }

    public int getEnderDragonKills() {
        return enderDragonKills;
    }

    public void setEnderDragonKills(int enderDragonKills) {
        this.enderDragonKills = enderDragonKills;
    }

    public int getWitherKills() {
        return witherKills;
    }

    public void setWitherKills(int witherKills) {
        this.witherKills = witherKills;
    }

    // ==================== 伤害计算 ====================

    /**
     * 根据等级计算基础攻击力加成
     */
    public float getAttackDamageBonus() {
        float perLevel = (float)(double) MaidWeaponConfig.LEVEL_DAMAGE_PER_LEVEL.get();
        float maxBonus = (float)(double) MaidWeaponConfig.LEVEL_MAX_BONUS.get();
        float bonus = (level - 1) * perLevel;
        if (level >= MAX_LEVEL) bonus += maxBonus;
        return bonus;
    }

    /**
     * 根据好感度计算伤害倍率（连续曲线，每点好感度都有影响）
     * 公式: base + (好感度 / 384) × slope
     */
    public float getFavorabilityDamageMultiplier() {
        float base = (float)(double) MaidWeaponConfig.FAV_BASE_MULTIPLIER.get();
        float slope = (float)(double) MaidWeaponConfig.FAV_SLOPE_MULTIPLIER.get();
        return base + (favorability / (float)MAX_FAVORABILITY) * slope;
    }

    // ==================== 罪恶系统 ====================

    /**
     * 获取已嵌入的罪恶列表
     */
    public List<SinType> getEmbeddedSins() {
        return new ArrayList<>(embeddedSins);
    }

    /**
     * 设置已嵌入的罪恶列表
     */
    public void setEmbeddedSins(List<SinType> sins) {
        this.embeddedSins = new ArrayList<>(sins);
    }

    /**
     * 嵌入一个罪恶
     * @return 是否成功嵌入
     */
    public boolean embedSin(SinType sin) {
        if (!SinSlotManager.canEmbed(embeddedSins, sin)) return false;
        this.embeddedSins = SinSlotManager.embed(embeddedSins, sin);
        return true;
    }

    /**
     * 移除一个罪恶
     * @return 是否成功移除
     */
    public boolean removeSin(SinType sin) {
        if (!embeddedSins.contains(sin)) return false;
        this.embeddedSins = SinSlotManager.remove(embeddedSins, sin);
        return true;
    }

    /**
     * 是否嵌入了傲慢之冠
     */
    public boolean hasPride() {
        return SinSlotManager.hasPride(embeddedSins);
    }

    /**
     * 是否嵌入了指定罪恶
     */
    public boolean hasSin(SinType type) {
        return SinSlotManager.hasSin(embeddedSins, type);
    }

    /**
     * 是否有任何罪恶
     */
    public boolean hasAnySin() {
        return !embeddedSins.isEmpty();
    }

    /**
     * 转为可读字符串（调试用）
     */
    @Override
    public String toString() {
        return String.format("MaidWeaponData{name='%s', Lv.%d, Favorability=%d, Kills=%d, Tier=%d, Dragon=%d, Wither=%d, Sins=%s}",
                maidName, level, favorability, totalKills, unlockedTier, enderDragonKills, witherKills,
                SinSlotManager.sinsToString(embeddedSins));
    }
}