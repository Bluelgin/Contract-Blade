package com.maidweapon.common;

import net.minecraftforge.common.ForgeConfigSpec;

/** Server authoritative tuning; all multipliers are fractions, not percentages. */
public final class OathConfig {
    private static final ForgeConfigSpec.Builder B = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec.DoubleValue PLAYER_DAMAGE = B.defineInRange("playerDamageBonus", .20, 0, 10);
    public static final ForgeConfigSpec.DoubleValue PLAYER_DEFENSE = B.defineInRange("playerDamageReduction", .10, 0, 1);
    public static final ForgeConfigSpec.DoubleValue MAID_DAMAGE = B.defineInRange("maidDamageBonus", .30, 0, 10);
    public static final ForgeConfigSpec.DoubleValue COOP_DAMAGE = B.defineInRange("cooperationDamageBonus", .15, 0, 10);
    public static final ForgeConfigSpec.IntValue COOP_INTERVAL = B.defineInRange("cooperationIntervalTicks", 20, 1, 1200);
    public static final ForgeConfigSpec.DoubleValue BURN = B.defineInRange("poolFractionBurnedPerSecond", .025, .000001, 1);
    public static final ForgeConfigSpec.DoubleValue BACKLASH_OUT = B.defineInRange("backlashDamageReductionPerLevel", .10, 0, .20);
    public static final ForgeConfigSpec.DoubleValue BACKLASH_IN = B.defineInRange("backlashDamageTakenPerLevel", .10, 0, 10);
    public static final ForgeConfigSpec.IntValue BACKLASH_DURATION = B.defineInRange("backlashDurationTicks", 3600, 1, 72000);
    public static final ForgeConfigSpec SPEC = B.build();
    private OathConfig() { }
}
