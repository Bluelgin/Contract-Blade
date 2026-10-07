package com.maidweapon.common;

import net.minecraftforge.common.ForgeConfigSpec;

/** Client comfort only; never changes combat timing or server rules. */
public final class BlackFoxClientConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.DoubleValue PARRY_SHAKE;
    public static final ForgeConfigSpec.BooleanValue BOSS_MUSIC;
    public static final ForgeConfigSpec.DoubleValue BOSS_MUSIC_VOLUME;
    static {
        var builder = new ForgeConfigSpec.Builder();
        builder.push("combatFeedback");
        PARRY_SHAKE = builder.comment("Parry camera shake strength. Set 0 to disable; does not change aiming or combat.")
                .defineInRange("parryShakeStrength", .65, 0, 1);
        builder.pop();
        builder.push("blackFoxMusic");
        BOSS_MUSIC = builder.comment("Play EpicBattle_J during your Black Fox encounter.").define("enabled", true);
        BOSS_MUSIC_VOLUME = builder.comment("Boss music multiplier, also controlled by Minecraft's Music volume slider.")
                .defineInRange("volume", .65, 0, 1);
        builder.pop(); SPEC = builder.build();
    }
    private BlackFoxClientConfig() { }
}
