package com.maidweapon.common;

import net.minecraftforge.common.ForgeConfigSpec;

/** Server-owned encounter difficulty. Does not configure player blades or their SA. */
public final class BlackFoxServerConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.DoubleValue DAMAGE_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue MAX_HEALTH;
    static {
        var builder = new ForgeConfigSpec.Builder();
        builder.comment("Black Fox challenge only. Values do not affect player weapons, reward SA or other mobs.",
                "仅调整黑狐挑战，不影响玩家武器、奖励 SA 或其他生物。")
                .push("blackFoxBoss");
        DAMAGE_MULTIPLIER = builder.comment(
                "Multiplier applied once before armor to all Boss damage against its challenger.",
                "1.0 = rebalanced defaults; 0.75 = 25% less damage; 0 = no damage.",
                "全部 Boss 伤害的统一倍率；1.0 为本版降伤后的默认值。可热重载。")
                .defineInRange("damageMultiplier", 1.0, 0.0, 10.0);
        MAX_HEALTH = builder.comment(
                "Maximum HP for newly started challenges (2 HP = 1 heart). Existing Bosses keep their health.",
                "新挑战的最大生命值。默认 400；已生成的 Boss 不会被重置或回血。范围受原版属性上限限制。")
                .defineInRange("maxHealth", 400.0, 1.0, 1024.0);
        builder.pop(); SPEC = builder.build();
    }
    private BlackFoxServerConfig() { }
}
