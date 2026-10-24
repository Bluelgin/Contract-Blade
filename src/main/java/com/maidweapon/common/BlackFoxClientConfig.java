package com.maidweapon.common;

import net.minecraftforge.common.ForgeConfigSpec;

/** Client comfort only; never changes combat timing or server rules. */
public final class BlackFoxClientConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.DoubleValue PARRY_SHAKE;
    public static final ForgeConfigSpec.BooleanValue BOSS_IMPACT_SHAKE;
    static {
        var builder = new ForgeConfigSpec.Builder();
        builder.push("combatFeedback");
        PARRY_SHAKE = builder.comment("Parry camera shake strength. Set 0 to disable; does not change aiming or combat.")
                .defineInRange("parryShakeStrength", .65, 0, 1);
        BOSS_IMPACT_SHAKE = builder.comment("Also shake briefly for nearby heavy Boss cuts. Parry shake strength controls both.")
                .define("bossImpactShake", true);
        builder.pop();
        SPEC = builder.build();
    }
    private BlackFoxClientConfig() { }
}
