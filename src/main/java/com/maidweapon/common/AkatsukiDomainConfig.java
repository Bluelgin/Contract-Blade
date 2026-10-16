package com.maidweapon.common;

import net.minecraftforge.common.ForgeConfigSpec;

/** Server-authoritative limits. Visual density lives in the separate client spec. */
public final class AkatsukiDomainConfig {
  public static final ForgeConfigSpec SPEC, CLIENT_SPEC;
  public static final ForgeConfigSpec.BooleanValue ENABLED, STRONG, BOSS_ADAPTERS, RESCUE, TINT;
  public static final ForgeConfigSpec.IntValue BUDGET_WINDOW,
      RESCUE_COOLDOWN,
      PROTECTION_TICKS,
      COLLAPSE_TICKS,
      REIGNITE_TICKS,
      CONTACT_GAP,
      EDGE_INTERVAL,
      SWORD_INTERVAL,
      RING_INTERVAL,
      COUNTER_INTERVAL,
      CHAIN_INTERVAL,
      RAIN_INTERVAL,
      RAIN_COUNT,
      MAX_TARGETS;
  public static final ForgeConfigSpec.DoubleValue RADIUS,
      RAMPAGE_ENTER,
      RAMPAGE_EXIT,
      RESCUE_THRESHOLD,
      RESCUE_HEALTH,
      RATIO_5,
      RATIO_9,
      RATIO_10,
      HIT_CAP,
      TARGET_SECOND,
      PLAYER_SECOND,
      DOMAIN_CAP,
      FALLBACK_HIT,
      FALLBACK_SECOND;
  public static final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> CHANNELS;
  public static final ForgeConfigSpec.IntValue QUALITY;
  public static final ForgeConfigSpec.DoubleValue DENSITY;

  static {
    var b = new ForgeConfigSpec.Builder();
    ENABLED = b.define("enabled", true);
    RADIUS = b.defineInRange("radius", 9d, 4d, 16d);
    BUDGET_WINDOW = b.defineInRange("budgetWindowTicks", 220, 20, 1200);
    CONTACT_GAP = b.defineInRange("contactMaintainTicks", 40, 10, 100);
    RAMPAGE_ENTER = b.defineInRange("rampageEnterFraction", .50, .05, .95);
    RAMPAGE_EXIT = b.defineInRange("rampageExitFraction", .55, .05, 1d);
    RESCUE = b.define("rescueEnabled", true);
    RESCUE_THRESHOLD = b.defineInRange("rescueThresholdFraction", .10, .01, .40);
    RESCUE_HEALTH = b.defineInRange("rescueHealthFraction", .25, .01, 1d);
    RESCUE_COOLDOWN = b.defineInRange("rescueCooldownTicks", 3600, 20, 72000);
    PROTECTION_TICKS = b.defineInRange("rescueProtectionTicks", 40, 1, 100);
    COLLAPSE_TICKS = b.defineInRange("rescueCollapseTicks", 12, 1, 20);
    REIGNITE_TICKS = b.defineInRange("rescueReigniteTicks", 4, 1, 10);
    EDGE_INTERVAL = b.defineInRange("afterimageInterval", 20, 10, 100);
    SWORD_INTERVAL = b.defineInRange("swordInterval", 30, 10, 200);
    RING_INTERVAL = b.defineInRange("ringInterval", 60, 20, 300);
    COUNTER_INTERVAL = b.defineInRange("counterInterval", 40, 20, 200);
    CHAIN_INTERVAL = b.defineInRange("chainInterval", 30, 20, 200);
    RAIN_INTERVAL = b.defineInRange("rainInterval", 100, 40, 400);
    RAIN_COUNT =
        b.comment("Client blades per rain cue; NOT damage calls.")
            .defineInRange("rainBlades", 48, 8, 96);
    MAX_TARGETS = b.defineInRange("maximumTargetsPerPulse", 4, 1, 8);
    RATIO_5 = b.defineInRange("limitFractionLevel5", .10, 0, .20);
    RATIO_9 = b.defineInRange("limitFractionLevel9", .15, 0, .20);
    RATIO_10 = b.defineInRange("limitFractionLevel10", .20, 0, .20);
    HIT_CAP = b.defineInRange("absoluteHitCapHP", 4d, .1, 4d);
    TARGET_SECOND = b.defineInRange("sharedTargetPerSecondHP", 4d, .1, 4d);
    PLAYER_SECOND = b.defineInRange("playerPerSecondHP", 6d, .1, 6d);
    DOMAIN_CAP =
        b.comment("Shared rolling combat window; never refilled by opening a domain.")
            .defineInRange("combatWindowHP", 24d, 1d, 24d);
    FALLBACK_HIT = b.defineInRange("unknownLimitHitHP", .5, .1, .5);
    FALLBACK_SECOND = b.defineInRange("unknownLimitPerSecondHP", 1d, .1, 1d);
    STRONG = b.define("allowAbsoluteChannel", true);
    BOSS_ADAPTERS =
        b.comment("Read-only public damageCap/DpsCap adapters; unknown revisions fall back.")
            .define("bossAdapters", true);
    CHANNELS =
        b.defineListAllowEmpty(
            "channels",
            java.util.List.of("slash", "rapid", "piercing", "arcane", "absolute"),
            v ->
                v instanceof String s
                    && java.util.Set.of("slash", "rapid", "piercing", "arcane", "absolute")
                        .contains(s));
    SPEC = b.build();
    var client = new ForgeConfigSpec.Builder();
    QUALITY =
        client
            .comment("0 minimal, 1 balanced, 2 full. Server combat is unchanged.")
            .defineInRange("quality", 1, 0, 2);
    DENSITY = client.defineInRange("visualDensity", 1d, .1, 1d);
    TINT =
        client
            .comment("Uses BladeTetra tint only; disable for shader packs or minimal visuals.")
            .define("rampageTint", true);
    CLIENT_SPEC = client.build();
  }

  private AkatsukiDomainConfig() {}
}
