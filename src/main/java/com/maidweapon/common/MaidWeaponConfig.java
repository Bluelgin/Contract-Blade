package com.maidweapon.common;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.common.Mod;
import org.apache.commons.lang3.tuple.Pair;

/**
 * ========================================
 * MaidWeapon 配置文件
 * ========================================
 *
 * 所有数值都可以调整，无需重新编译。
 * 配置文件位于 .minecraft/config/maid_weapon.toml
 */
@Mod.EventBusSubscriber
public class MaidWeaponConfig {

    // ==================== 契约共鸣 ====================

    public static ForgeConfigSpec.IntValue RESONANCE_COMBAT_DRAIN_INTERVAL;
    public static ForgeConfigSpec.IntValue RESONANCE_RECOVERY_DELAY;
    public static ForgeConfigSpec.IntValue RESONANCE_PASSIVE_RECOVERY;
    public static ForgeConfigSpec.IntValue RESONANCE_COOP_REWARD;
    public static ForgeConfigSpec.IntValue RESONANCE_DEATH_PENALTY;
    public static ForgeConfigSpec.IntValue RESONANCE_MAID_EMERGENCY_COST;
    public static ForgeConfigSpec.IntValue RESONANCE_TREE_RECOVERY;
    public static ForgeConfigSpec.DoubleValue RESONANCE_FOOD_MULTIPLIER;
    public static ForgeConfigSpec.DoubleValue RESONANCE_HEAL_MULTIPLIER;
    public static ForgeConfigSpec.DoubleValue RESONANCE_OWNER_HURT_MULTIPLIER;

    // ==================== 契约显现过渡 ====================

    public static ForgeConfigSpec.IntValue MANIFEST_DEPLOY_DELAY;
    public static ForgeConfigSpec.IntValue MANIFEST_RECALL_DELAY;
    public static ForgeConfigSpec.IntValue MANIFEST_HANDOVER_DELAY;

    // ==================== 等级 ====================

    /** 每级攻击力加成 */
    public static ForgeConfigSpec.DoubleValue LEVEL_DAMAGE_PER_LEVEL;
    /** 满级额外攻击力 */
    public static ForgeConfigSpec.DoubleValue LEVEL_MAX_BONUS;

    // ==================== 好感度倍率 ====================

    /** 好感度倍率基数（公式底部） */
    public static ForgeConfigSpec.DoubleValue FAV_BASE_MULTIPLIER;
    /** 好感度倍率斜率（每点好感度增加量） */
    public static ForgeConfigSpec.DoubleValue FAV_SLOPE_MULTIPLIER;
    public static ForgeConfigSpec.BooleanValue SHOW_RESONANCE_HUD;
    public static ForgeConfigSpec.IntValue RESONANCE_HUD_OFFSET_Y;

    public static final ForgeConfigSpec SPEC;
    static {
        Pair<MaidWeaponConfig, ForgeConfigSpec> pair =
                new ForgeConfigSpec.Builder().configure(MaidWeaponConfig::new);
        SPEC = pair.getRight();
    }

    private MaidWeaponConfig(ForgeConfigSpec.Builder builder) {
        builder.push("ContractResonance");

        RESONANCE_COMBAT_DRAIN_INTERVAL = builder
                .comment("显现女仆处于战斗时每消耗 1 点共鸣的 tick 间隔")
                .defineInRange("combatDrainInterval", 80, 20, 1200);
        RESONANCE_RECOVERY_DELAY = builder
                .comment("脱离战斗后开始自然恢复所需 tick")
                .defineInRange("recoveryDelay", 100, 0, 2400);
        RESONANCE_PASSIVE_RECOVERY = builder
                .comment("脱战时每秒恢复的共鸣")
                .defineInRange("passiveRecovery", 2, 0, 200);
        RESONANCE_COOP_REWARD = builder
                .comment("主人与女仆协同命中同一目标时恢复的共鸣，每秒至多一次")
                .defineInRange("cooperationReward", 3, 0, 200);
        RESONANCE_DEATH_PENALTY = builder
                .comment("主人在成长挑战中死亡时损失的共鸣")
                .defineInRange("challengeDeathPenalty", 30, 0, 200);
        RESONANCE_MAID_EMERGENCY_COST = builder
                .comment("女仆濒死时额外损失的共鸣并进入保护性收回")
                .defineInRange("maidEmergencyCost", 20, 0, 200);
        RESONANCE_TREE_RECOVERY = builder
                .comment("神树附近每秒为每份契约恢复的共鸣")
                .defineInRange("sacredTreeRecovery", 5, 0, 200);
        RESONANCE_FOOD_MULTIPLIER = builder
                .comment("主人进食时，共鸣恢复 = 饱食度增量 × 此倍率")
                .defineInRange("foodRecoveryMultiplier", 1.0, 0.0, 100.0);
        RESONANCE_HEAL_MULTIPLIER = builder
                .comment("主人回血时，共鸣恢复 = 血量增量 × 此倍率")
                .defineInRange("healRecoveryMultiplier", 1.0, 0.0, 100.0);
        RESONANCE_OWNER_HURT_MULTIPLIER = builder
                .comment("主人受伤时，共鸣恢复 = 失去血量 × 此倍率")
                .defineInRange("ownerHurtRecoveryMultiplier", 0.3, 0.0, 10.0);

        builder.pop();
        builder.push("ContractManifestation");

        MANIFEST_DEPLOY_DELAY = builder
                .comment("切到自动契约武器后延迟显现的 tick；用于过滤滚轮快速划过")
                .defineInRange("deployDelayTicks", 8, 0, 200);
        MANIFEST_RECALL_DELAY = builder
                .comment("离开自动契约武器后延迟收回的 tick；期间切回可取消收回")
                .defineInRange("recallDelayTicks", 30, 0, 400);
        MANIFEST_HANDOVER_DELAY = builder
                .comment("从一名契约女仆切换到另一名契约女仆时的交接 tick")
                .defineInRange("handoverDelayTicks", 12, 0, 200);

        builder.pop();
        builder.push("Level");

        LEVEL_DAMAGE_PER_LEVEL = builder
                .comment("每级攻击力加成", "默认 1.0")
                .defineInRange("levelDamagePerLevel", 1.0, 0.0, 100.0);

        LEVEL_MAX_BONUS = builder
                .comment("满级额外攻击力", "默认 3.0")
                .defineInRange("levelMaxBonus", 3.0, 0.0, 100.0);

        builder.pop();
        builder.push("FavorabilityMultiplier");

        FAV_BASE_MULTIPLIER = builder
                .comment("好感度只提供额外伤害；0 好感不削弱原始伤害")
                .defineInRange("favorabilityBaseMultiplierV2", 1.0, 0.0, 10.0);

        FAV_SLOPE_MULTIPLIER = builder
                .comment("满好感时的额外契约伤害倍率，默认 +25%")
                .defineInRange("favorabilityMaxBonusV2", 0.25, 0.0, 10.0);

        builder.pop();
        builder.push("Hud");

        SHOW_RESONANCE_HUD = builder
                .comment("是否显示契约共鸣条")
                .define("showContractResonanceHud", true);

        RESONANCE_HUD_OFFSET_Y = builder
                .comment("契约共鸣条向上偏移的像素数；与其他 Mod HUD 冲突时可调大")
                .defineInRange("contractResonanceHudOffsetY", 0, -100, 200);

        builder.pop();
    }
}
