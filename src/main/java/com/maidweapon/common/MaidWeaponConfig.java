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

    // ==================== 好感度 ====================

    /** 击杀怪物消耗倍率（分母，怪物血量÷此值=消耗） */
    public static ForgeConfigSpec.IntValue KILL_CONSUMPTION_DIVISOR;
    /** 被怪物打恢复倍率（受伤×此值，取整最少+1） */
    public static ForgeConfigSpec.DoubleValue HURT_RECOVERY_MULTIPLIER;
    /** 进食恢复倍率（饱食度增加×此值） */
    public static ForgeConfigSpec.DoubleValue FOOD_RECOVERY_MULTIPLIER;
    /** 回血恢复倍率（恢复血量×此值） */
    public static ForgeConfigSpec.DoubleValue HEAL_RECOVERY_MULTIPLIER;
    /** 每日自然恢复量 */
    public static ForgeConfigSpec.IntValue DAILY_RECOVERY;
    /** 玩家死亡惩罚 */
    public static ForgeConfigSpec.IntValue DEATH_PENALTY;

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

    public static final ForgeConfigSpec SPEC;
    static {
        Pair<MaidWeaponConfig, ForgeConfigSpec> pair =
                new ForgeConfigSpec.Builder().configure(MaidWeaponConfig::new);
        SPEC = pair.getRight();
    }

    private MaidWeaponConfig(ForgeConfigSpec.Builder builder) {
        builder.push("Favorability");

        KILL_CONSUMPTION_DIVISOR = builder
                .comment("击杀消耗分母（怪物HP÷此值=好感度消耗）", "默认 20 → 20HP怪=-1")
                .defineInRange("killConsumptionDivisor", 20, 1, 1000);

        HURT_RECOVERY_MULTIPLIER = builder
                .comment("被击恢复倍率（受伤×此值，最少+1）", "默认 0.3")
                .defineInRange("hurtRecoveryMultiplier", 0.3, 0.0, 10.0);

        FOOD_RECOVERY_MULTIPLIER = builder
                .comment("进食恢复倍率（饱食度增加×此值）", "默认 1.0")
                .defineInRange("foodRecoveryMultiplier", 1.0, 0.0, 100.0);

        HEAL_RECOVERY_MULTIPLIER = builder
                .comment("回血恢复倍率（恢复血量×此值）", "默认 2.0")
                .defineInRange("healRecoveryMultiplier", 2.0, 0.0, 100.0);

        DAILY_RECOVERY = builder
                .comment("每日自然恢复好感度", "默认 80")
                .defineInRange("dailyRecovery", 80, 0, 384);

        DEATH_PENALTY = builder
                .comment("玩家死亡惩罚好感度", "默认 30")
                .defineInRange("deathPenalty", 30, 0, 384);

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
                .comment("好感度倍率基数（0好感时的倍率）", "默认 0.5")
                .defineInRange("favBaseMultiplier", 0.5, 0.0, 10.0);

        FAV_SLOPE_MULTIPLIER = builder
                .comment("好感度倍率斜率（满好感时+此值）", "默认 0.7 → 满好感=1.2")
                .defineInRange("favSlopeMultiplier", 0.7, 0.0, 10.0);

        builder.pop();
    }
}
