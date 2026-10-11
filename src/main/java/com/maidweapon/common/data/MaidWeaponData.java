package com.maidweapon.common.data;

import com.maidweapon.common.MaidWeaponConfig;
import com.maidweapon.common.ContractRulesConfig;
import com.maidweapon.common.sin.SinType;
import com.maidweapon.common.sin.SinSlotManager;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 女仆/契约通道独立的关系数据：名称、好感度、共鸣与旧 Part 档案。
 * 玩家共享成长由 ContractMasteryData/ContractMasteryService 管理。
 * 下方 deprecated 成长方法只用于读取旧物品存档和二进制兼容，不能作为战斗等级来源。
 */
public class MaidWeaponData {

    // ==================== 基础信息 ====================

    /** 女仆名称 */
    private String maidName = "Unknown Maid";

    /** 旧物品成长档案；玩家当前熟练度由 ContractMasteryService 提供。 */
    private final ContractMasteryData legacyGrowth = new ContractMasteryData();

    /** 好感度（0 ~ 384，对应 TLM 好感度） */
    private int favorability = 0;

    /** 契约共鸣（0 ~ 200，负责显现、施法与战斗维持） */
    private int resonance = MAX_RESONANCE;

    // ==================== 战斗统计 ====================

    /** Detached archive only. Never use this as a combat authority. */
    public ContractMasteryData legacyGrowth() { return legacyGrowth.copy(); }
    @Deprecated public int[] getUpgradeProgress() { return legacyGrowth.getUpgradeProgress(); }
    @Deprecated public void setUpgradeProgress(int[] progress) { legacyGrowth.setUpgradeProgress(progress); }
    public static int maximumResonance() { return ContractRulesConfig.maximumResonance(); }

    // ==================== Legacy Part archive ====================

    /**
     * Serialized seven-sins payload from the paused Part campaign.
     * Core preserves it for old worlds but must not use it for gameplay.
     */
    private List<SinType> embeddedSins = new ArrayList<>();

    // ==================== 常量 ====================

    /** 最大等级 */
    public static final int MAX_LEVEL = 10;

    /** 最大好感度（TLM 上限 384） */
    public static final int MAX_FAVORABILITY = 384;

    /** 最小好感度 */
    public static final int MIN_FAVORABILITY = 0;

    /** 契约共鸣上限 */
    public static final int MAX_RESONANCE = 200;

    /** 契约共鸣下限 */
    public static final int MIN_RESONANCE = 0;

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
    @Deprecated public static final int[] TIER_REQUIREMENT = ContractMasteryData.TIER_REQUIREMENT.clone();

    // ==================== 构造器 ====================

    public MaidWeaponData() {}

    public MaidWeaponData(String maidName) {
        this.maidName = maidName;
    }

    // ==================== 等级 ====================

    /** @deprecated Item growth is an old-save archive. Use ContractMasteryService with a player. */
    @Deprecated public int getLevel() { return legacyGrowth.getLevel(); }
    @Deprecated public void setLevel(int value) { legacyGrowth.setLevel(value); }
    @Deprecated public boolean tryUpgrade(int tier, boolean dragon, boolean wither) { return legacyGrowth.tryUpgrade(tier, dragon, wither); }
    @Deprecated public boolean tryUpgrade(int tier, boolean dragon, boolean wither, String id) { return legacyGrowth.tryUpgrade(tier, dragon, wither, id); }
    @Deprecated public String getNextUpgradeHint() { return legacyGrowth.getNextUpgradeHint(); }
    @Deprecated public int getNextUpgradeProgress() { return legacyGrowth.getNextUpgradeProgress(); }

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

    // ==================== 契约共鸣 ====================

    public int getResonance() {
        return Math.min(resonance, maximumResonance());
    }

    public void setResonance(int resonance) {
        this.resonance = Math.max(MIN_RESONANCE, Math.min(resonance, maximumResonance()));
    }

    public void addResonance(int amount) {
        setResonance(getResonance() + amount);
    }

    public void reduceResonance(int amount) {
        setResonance(getResonance() - amount);
    }

    // ==================== 战斗统计 ====================

    public String getMaidName() {
        return maidName;
    }

    public void setMaidName(String maidName) {
        this.maidName = maidName;
    }

    @Deprecated public int getTotalKills() { return legacyGrowth.getTotalKills(); }
    @Deprecated public void setTotalKills(int value) { legacyGrowth.setTotalKills(value); }
    @Deprecated public void addKill() { legacyGrowth.addKill(); }
    @Deprecated public int getUnlockedTier() { return legacyGrowth.getUnlockedTier(); }
    @Deprecated public void setUnlockedTier(int value) { legacyGrowth.setUnlockedTier(value); }
    @Deprecated public int getEnderDragonKills() { return legacyGrowth.getEnderDragonKills(); }
    @Deprecated public void setEnderDragonKills(int value) { legacyGrowth.setEnderDragonKills(value); }
    @Deprecated public int getWitherKills() { return legacyGrowth.getWitherKills(); }
    @Deprecated public void setWitherKills(int value) { legacyGrowth.setWitherKills(value); }
    @Deprecated public float getAttackDamageBonus() { return legacyGrowth.getAttackDamageBonus(); }

    /**
     * 根据好感度计算伤害倍率（连续曲线，每点好感度都有影响）
     * 公式: base + (好感度 / 384) × slope
     */
    public float getFavorabilityDamageMultiplier() {
        float base = (float)(double) MaidWeaponConfig.FAV_BASE_MULTIPLIER.get();
        float slope = (float)(double) MaidWeaponConfig.FAV_SLOPE_MULTIPLIER.get();
        return base + (favorability / (float)MAX_FAVORABILITY) * slope;
    }

    // ==================== Legacy Part archive access ====================

    /**
     * Returns archived seven-sins data retained for old saves.
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
        return String.format("MaidWeaponData{name='%s', Lv.%d, Favorability=%d, Resonance=%d, Kills=%d, Tier=%d, Dragon=%d, Wither=%d, Sins=%s}",
                maidName, getLevel(), favorability, resonance, getTotalKills(), getUnlockedTier(), getEnderDragonKills(), getWitherKills(),
                SinSlotManager.sinsToString(embeddedSins));
    }
}
