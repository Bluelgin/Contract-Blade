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
    public static ForgeConfigSpec.DoubleValue RESONANCE_FOOD_MULTIPLIER;
    public static ForgeConfigSpec.DoubleValue RESONANCE_HEAL_MULTIPLIER;
    public static ForgeConfigSpec.DoubleValue RESONANCE_OWNER_HURT_MULTIPLIER;

    // ==================== 契约显现过渡 ====================

    public static ForgeConfigSpec.IntValue MANIFEST_DEPLOY_DELAY;
    public static ForgeConfigSpec.IntValue MANIFEST_RECALL_DELAY;
    public static ForgeConfigSpec.IntValue MANIFEST_HANDOVER_DELAY;

    // ==================== Contract NBT safety ====================

    public static ForgeConfigSpec.IntValue CONTRACT_NBT_WARNING_BYTES;
    public static ForgeConfigSpec.IntValue CONTRACT_NBT_HIGH_WARNING_BYTES;
    public static ForgeConfigSpec.IntValue CONTRACT_NBT_CRITICAL_WARNING_BYTES;
    public static ForgeConfigSpec.IntValue CONTRACT_NBT_MAX_DECOMPRESSED_BYTES;
    public static ForgeConfigSpec.IntValue CONTRACT_NBT_MAX_DEPTH;
    public static ForgeConfigSpec.IntValue CONTRACT_NBT_MAX_INTRINSIC_SPIRITS;

    // ==================== 安全照料模式 ====================

    public static ForgeConfigSpec.BooleanValue ENABLE_SAFE_FEEDING;
    public static ForgeConfigSpec.IntValue SAFE_FEEDING_HUNGER_THRESHOLD;
    public static ForgeConfigSpec.IntValue SAFE_FEEDING_DELAY;
    public static ForgeConfigSpec.DoubleValue SAFE_FEEDING_DANGER_RADIUS;

    // ==================== 契约关注提醒 ====================

    public static ForgeConfigSpec.BooleanValue ENABLE_ATTENTION_REMINDERS;
    public static ForgeConfigSpec.IntValue ATTENTION_DELAY_TICKS;
    public static ForgeConfigSpec.BooleanValue ATTENTION_CHIME;
    public static ForgeConfigSpec.BooleanValue ATTENTION_VOICE_ON_MANIFEST;

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
    public static ForgeConfigSpec.IntValue SHRINE_WHITE_FOX_KILLS;
    static {
        Pair<MaidWeaponConfig, ForgeConfigSpec> pair =
                new ForgeConfigSpec.Builder().configure(MaidWeaponConfig::new);
        SPEC = pair.getRight();
    }

    private MaidWeaponConfig(ForgeConfigSpec.Builder builder) {
        builder.push("ShinkitsuShrine");
        SHRINE_WHITE_FOX_KILLS = builder
                .comment("Total kill count on carried SlashBlades required before offering one cake to White Fox")
                .defineInRange("whiteFoxRequiredKills", 520, 0, Integer.MAX_VALUE);
        builder.pop();
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
        builder.push("ContractNbtSafety");

        CONTRACT_NBT_WARNING_BYTES = builder
                .comment("Warn when the complete contract item NBT reaches this uncompressed size")
                .defineInRange("warningBytes", 262144, 65536, 8388608);
        CONTRACT_NBT_HIGH_WARNING_BYTES = builder
                .comment("Second-level warning for complete contract item NBT")
                .defineInRange("highWarningBytes", 524288, 131072, 8388608);
        CONTRACT_NBT_CRITICAL_WARNING_BYTES = builder
                .comment("Critical warning for complete contract item NBT; recall is still attempted")
                .defineInRange("criticalWarningBytes", 1048576, 262144, 16777216);
        CONTRACT_NBT_MAX_DECOMPRESSED_BYTES = builder
                .comment("Absolute decompressed MaidEntityData safety limit used against compression bombs")
                .defineInRange("maxDecompressedMaidBytes", 16777216, 1048576, 67108864);
        CONTRACT_NBT_MAX_DEPTH = builder
                .comment("Maximum permitted nesting depth inside compressed maid entity data")
                .defineInRange("maxDepth", 128, 16, 512);
        CONTRACT_NBT_MAX_INTRINSIC_SPIRITS = builder
                .comment("Maximum number of complete intrinsic spirit contracts on one weapon")
                .defineInRange("maxIntrinsicSpirits", 8, 1, 64);

        builder.pop();
        builder.push("SafeFeeding");

        ENABLE_SAFE_FEEDING = builder
                .comment("安全且主人饥饿时，显现女仆是否临时切换为喂食工作模式")
                .define("enabled", true);
        SAFE_FEEDING_HUNGER_THRESHOLD = builder
                .comment("玩家饥饿值不高于此值时，安全状态下触发喂食任务")
                .defineInRange("hungerThreshold", 14, 0, 19);
        SAFE_FEEDING_DELAY = builder
                .comment("最后一次发现危险后，等待多少 tick 才允许进入喂食模式")
                .defineInRange("safeDelayTicks", 120, 0, 1200);
        SAFE_FEEDING_DANGER_RADIUS = builder
                .comment("检查敌对生物的安全半径")
                .defineInRange("dangerRadius", 12.0, 2.0, 64.0);

        builder.pop();
        builder.push("ContractAttention");

        ENABLE_ATTENTION_REMINDERS = builder
                .comment("女仆长时间未显现时，是否通过契约提醒主人")
                .define("enabled", true);
        ATTENTION_DELAY_TICKS = builder
                .comment("女仆在主人背包中累计多少 tick 未显现后想要得到关注；48000 为两个游戏日")
                .defineInRange("attentionDelayTicks", 48000, 1200, 7200000);
        ATTENTION_CHIME = builder
                .comment("首次契约回响是否伴随轻微的紫水晶提示音")
                .define("playChime", true);
        ATTENTION_VOICE_ON_MANIFEST = builder
                .comment("回应提醒后成功显现时，是否播放 TLM 原生待机语音")
                .define("voiceAfterManifest", true);

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
