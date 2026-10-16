package com.maidweapon.common;

import net.minecraftforge.common.ForgeConfigSpec;

/** Client-only voice personality; no authoritative contract/combat impact. */
public final class ContractVoiceClientConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.DoubleValue VOLUME;
    public static final ForgeConfigSpec.DoubleValue GREETING_CHANCE;
    public static final ForgeConfigSpec.DoubleValue CURIOUS_CHANCE;
    public static final ForgeConfigSpec.DoubleValue PLAYFUL_CHANCE;
    public static final ForgeConfigSpec.IntValue GREETING_VARIANT;
    public static final ForgeConfigSpec.IntValue CURIOUS_VARIANT;
    public static final ForgeConfigSpec.IntValue PLAYFUL_VARIANT;

    static {
        var b = new ForgeConfigSpec.Builder();
        b.push("inventoryVoice");
        ENABLED = b.comment(
                "Use TLM's built-in LittleMaid PECO voice in the survival inventory.",
                "No voice files are included; the sword never plays a system item-get sound.")
                .define("enabled", true);
        VOLUME = b.comment("Respects Minecraft's Neutral sound slider.")
                .defineInRange("volume", .60, 0, 1);
        GREETING_CHANCE = b.comment("Chance to respond to the first real pickup.")
                .defineInRange("greetingChance", .40, 0, 1);
        CURIOUS_CHANCE = b.comment("Chance to respond to quickly lifting the SAME blade again.")
                .defineInRange("curiousChance", .80, 0, 1);
        PLAYFUL_CHANCE = b.comment("Chance for a rare reaction on 3+ rapid lifts.")
                .defineInRange("playfulChance", .35, 0, 1);
        b.comment("Optional sample selection within the listed PECO event category.",
                "-1 = random among all available clips; 0+ = exact indexed clip.",
                "Indexes must be manually auditioned: category alone does not confirm meaning.");
        GREETING_VARIANT = b.defineInRange("greetingVariant", -1, -1, 255);
        CURIOUS_VARIANT = b.defineInRange("curiousVariant", -1, -1, 255);
        PLAYFUL_VARIANT = b.defineInRange("playfulVariant", -1, -1, 255);
        b.pop();
        SPEC = b.build();
    }

    private ContractVoiceClientConfig() {}
}
