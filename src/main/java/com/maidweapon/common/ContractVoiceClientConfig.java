package com.maidweapon.common;

import net.minecraftforge.common.ForgeConfigSpec;

/** Client-only inventory voice comfort settings; no authoritative game rules. */
public final class ContractVoiceClientConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.DoubleValue VOLUME;

    static {
        var builder = new ForgeConfigSpec.Builder();
        builder.push("inventoryVoice");
        ENABLED = builder.comment(
                "Play the built-in Touhou Little Maid LittleMaid PECO voice when lifting a bound",
                "contract weapon with the mouse in the player inventory. No sound files are bundled.")
                .define("enabled", true);
        VOLUME = builder.comment(
                "Contract inventory voice volume. Respects Minecraft Neutral sound slider.")
                .defineInRange("volume", .60, 0, 1);
        builder.pop();
        SPEC = builder.build();
    }

    private ContractVoiceClientConfig() {}
}
