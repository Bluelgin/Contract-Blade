package com.maidweapon.common.data;

import com.maidweapon.common.ContractRulesConfig;
import com.maidweapon.common.MaidWeaponConfig;

/** Player-owned progression. Mutable only inside the server service; public reads are detached snapshots. */
public final class ContractMasteryData {
    public static final int SCHEMA_VERSION = 1, MAX_LEVEL = 10, TIER_5 = 5;
    public static final int[] TIER_REQUIREMENT = {1, 2, 3, 4, 5, 5, 5, 5, 5};
    private int level = 1, totalKills, unlockedTier, enderDragonKills, witherKills;
    private int[] upgradeProgress = new int[MAX_LEVEL + 1];
    public int[] getUpgradeProgress() { return upgradeProgress.clone(); }
    public void setUpgradeProgress(int[] values) {
        upgradeProgress = new int[MAX_LEVEL + 1];
        for (int i = 0; i < Math.min(values.length, upgradeProgress.length); i++) upgradeProgress[i] = Math.max(0, Math.min(1000000, values[i]));
    }
    private static int bounded(int value) { return Math.max(0, value); }
    private static int increment(int value) { return value == Integer.MAX_VALUE ? value : value + 1; }
    public ContractMasteryData copy() {
        var result = new ContractMasteryData();
        result.level = level; result.totalKills = totalKills; result.unlockedTier = unlockedTier;
        result.enderDragonKills = enderDragonKills; result.witherKills = witherKills;
        result.setUpgradeProgress(upgradeProgress); return result;
    }
    /** Conservative, idempotent join. Caller records source receipts separately. Never sums weapon statistics. */
    public void mergeLegacy(ContractMasteryData old) {
        level = Math.max(level, old.level); totalKills = Math.max(totalKills, old.totalKills);
        unlockedTier = Math.max(unlockedTier, old.unlockedTier);
        enderDragonKills = Math.max(enderDragonKills, old.enderDragonKills);
        witherKills = Math.max(witherKills, old.witherKills);
        for (int i = 0; i <= MAX_LEVEL; i++) upgradeProgress[i] = Math.max(upgradeProgress[i], old.upgradeProgress[i]);
    }
    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = Math.max(1, Math.min(level, MAX_LEVEL));
    }

    /**
     * 尝试提升玩家契约熟练度
     * @param defeatedTier 被击杀怪物的Tier
     * @return 是否成功升级
     */
    public boolean tryUpgrade(int defeatedTier, boolean isEnderDragon, boolean isWither) {
        return tryUpgrade(defeatedTier, isEnderDragon, isWither,
                isEnderDragon ? "minecraft:ender_dragon" : isWither ? "minecraft:wither" : "");
    }

    public boolean tryUpgrade(int defeatedTier, boolean isEnderDragon, boolean isWither, String entityId) {
        if (level >= MAX_LEVEL) return false;

        var rule = ContractRulesConfig.LEVELS[level + 1];
        if (rule.custom()) {
            if (!rule.matches(defeatedTier, entityId)) return false;
            int target = level + 1;
            if (upgradeProgress.length == 0) upgradeProgress = new int[MAX_LEVEL + 1];
            upgradeProgress[target] = Math.min(1000000, upgradeProgress[target] + 1);
            if (upgradeProgress[target] < rule.kills().get()) return false;
            level++;
            unlockedTier = Math.max(unlockedTier, Math.min(TIER_5, defeatedTier));
            return true;
        }

        int requiredTier = TIER_REQUIREMENT[level - 1];

        // 基本条件：击杀怪物的Tier必须达到要求
        if (defeatedTier < requiredTier) return false;

        // 特殊条件检查（Lv.7+）
        if (level >= 5) {
            // 记录Boss击杀
            if (isEnderDragon) enderDragonKills = increment(enderDragonKills);
            if (isWither) witherKills = increment(witherKills);

            // Lv.5 → Lv.6: 必须击杀末影龙
            if (level == 5 && !isEnderDragon) return false;

            // Lv.6 → Lv.7: 必须击杀凋灵
            if (level == 6 && !isWither) return false;

            // Lv.7 → Lv.8: 末影龙+凋灵各击杀3次
            if (level == 7) {
                if (enderDragonKills < 3 || witherKills < 3) return false;
            }

            // Lv.8 → Lv.9: 需要击杀Boss 5次
            if (level == 8 && ((long)enderDragonKills + witherKills) < 5) return false;

            // Lv.9 → Lv.10: 需要击杀Boss 10次
            if (level == 9 && ((long)enderDragonKills + witherKills) < 10) return false;
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

        var rule = ContractRulesConfig.LEVELS[level + 1];
        if (rule.custom()) {
            String target = rule.mode().get().equals("entities")
                    ? String.join(" / ", rule.entities().get()) : "TIER " + rule.tier().get();
            return "需要无死亡击杀 " + target + "（" + getNextUpgradeProgress() + "/" + rule.kills().get() + "）";
        }

        if (level == 5) return "需要无死亡击杀末影龙";
        if (level == 6) return "需要无死亡击杀凋灵";
        if (level == 7) return "需要无死亡击杀末影龙和凋灵各3次";
        if (level == 8) return "需要累计无死亡击杀末影龙与凋灵共5次";
        if (level == 9) return "需要累计无死亡击杀末影龙与凋灵共10次";

        int requiredTier = TIER_REQUIREMENT[level - 1];
        return "需要无死亡击杀 TIER " + requiredTier + " 级怪物";
    }

    public int getNextUpgradeProgress() {
        return level >= MAX_LEVEL || upgradeProgress.length == 0 ? 0 : upgradeProgress[level + 1];
    }

    public int getTotalKills() {
        return totalKills;
    }

    public void setTotalKills(int totalKills) {
        this.totalKills = bounded(totalKills);
    }

    public void addKill() {
        this.totalKills = increment(this.totalKills);
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
        this.enderDragonKills = bounded(enderDragonKills);
    }

    public int getWitherKills() {
        return witherKills;
    }

    public void setWitherKills(int witherKills) {
        this.witherKills = bounded(witherKills);
    }

    public float getAttackDamageBonus() {
        double override = ContractRulesConfig.LEVELS[level].damage().get();
        if (override >= 0) return (float) override;
        float perLevel = (float)(double) MaidWeaponConfig.LEVEL_DAMAGE_PER_LEVEL.get();
        float maxBonus = (float)(double) MaidWeaponConfig.LEVEL_MAX_BONUS.get();
        float bonus = (level - 1) * perLevel;
        if (level >= MAX_LEVEL) bonus += maxBonus;
        return bonus;
    }

}
