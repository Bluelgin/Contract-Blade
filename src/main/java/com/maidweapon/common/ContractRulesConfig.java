package com.maidweapon.common;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;

/** World-scoped gameplay rules, automatically synchronized by Forge to clients. */
public final class ContractRulesConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue AUTO_MANIFEST, AUTO_COMBAT, AUTO_FEED;
    public static final ForgeConfigSpec.IntValue HURT_COOLDOWN, HURT_MIN_RESONANCE, MAX_RESONANCE, KILL_REWARD, MAX_FOLLOWERS;
    public static final LevelRule[] LEVELS = new LevelRule[11];

    public record LevelRule(ForgeConfigSpec.ConfigValue<String> mode,
                            ForgeConfigSpec.IntValue tier, ForgeConfigSpec.IntValue kills,
                            ForgeConfigSpec.ConfigValue<List<? extends String>> entities,
                            ForgeConfigSpec.DoubleValue damage) {
        public boolean custom() { return !mode.get().equals("legacy"); }
        public boolean matches(int defeatedTier, String entityId) {
            return mode.get().equals("entities") ? entities.get().contains(entityId) : defeatedTier >= tier.get();
        }
    }

    static {
        var b = new ForgeConfigSpec.Builder();
        b.push("Manifestation");
        MAX_FOLLOWERS = b.comment("同时跟随的契约女仆上限；家模式/待命不计入。超限时拒绝召唤或恢复驻留。")
                .defineInRange("maxFollowingMaids", 1, 1, 8);
        AUTO_MANIFEST = b.comment("受伤后自动显现女仆；仅限手持自己的契约武器，默认关闭。")
                .define("autoManifestWhenHurt", false);
        HURT_COOLDOWN = b.comment("自动显现冷却，单位 tick（20 tick = 1 秒）。")
                .defineInRange("hurtCooldownTicks", 200, 0, 72000);
        HURT_MIN_RESONANCE = b.comment("自动显现所需最低共鸣；仍须满足正常显现条件。")
                .defineInRange("hurtMinimumResonance", 1, 0, 1000000);
        b.pop().push("AutomaticWork");
        AUTO_COMBAT = b.comment("共鸣武器饰品允许自动切换战斗任务；不会关闭武器投影。")
                .define("combatTask", true);
        AUTO_FEED = b.comment("允许临时切换安全喂食任务；还需 common 配置 SafeFeeding.enabled 开启。")
                .define("feedingTask", true);
        b.pop().push("Resonance");
        MAX_RESONANCE = b.comment("共鸣上限。提高上限不会补满已有契约；旧存档缺失字段仍按 200 处理。",
                "恢复/消耗倍率继续使用 config/maid_weapon-common.toml 的 ContractResonance 配置。")
                .defineInRange("maximum", 200, 1, 1000000);
        KILL_REWARD = b.comment("契约武器合格击杀后的共鸣恢复量；0 关闭击杀恢复。")
                .defineInRange("killRecovery", 5, 0, 1000000);
        b.pop().push("Growth");
        for (int level = 1; level <= 10; level++) {
            b.push("level" + level);
            var mode = b.comment("升到本级的规则：legacy 原规则 / tier 强度等级 / entities 列表任意一种实体。level1 不使用升级规则。")
                    .define("mode", "legacy", value -> value instanceof String s
                            && List.of("legacy", "tier", "entities").contains(s));
            var tier = b.defineInRange("requiredTier", Math.min(5, Math.max(1, level - 1)), 0, 5);
            var kills = b.comment("自定义规则需要的合格击杀次数，仅计算当前等级的击杀。")
                    .defineInRange("requiredKills", 1, 1, 1000000);
            var ids = b.comment("entities 模式使用实体注册 ID，例如 minecraft:wither；空列表不会升级。")
                    .defineListAllowEmpty("entityIds", List.<String>of(), value -> value instanceof String s
                            && ResourceLocation.tryParse(s) != null);
            var damage = b.comment("本级额外攻击力；-1 使用原 common 配置的等级公式。不是原武器总伤害。")
                    .defineInRange("damageBonus", -1.0, -1.0, 10000.0);
            LEVELS[level] = new LevelRule(mode, tier, kills, ids, damage);
            b.pop();
        }
        b.pop();
        SPEC = b.build();
    }

    public static int maximumResonance() {
        return SPEC.isLoaded() ? MAX_RESONANCE.get() : 200;
    }
    private ContractRulesConfig() { }
}
