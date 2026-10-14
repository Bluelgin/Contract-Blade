package com.maidweapon.common;

import net.minecraftforge.common.ForgeConfigSpec;

/** Server-authoritative limits. Visual density lives in the separate client spec. */
public final class AkatsukiDomainConfig {
  public static final ForgeConfigSpec SPEC, CLIENT_SPEC;
  public static final ForgeConfigSpec.BooleanValue ENABLED, STRONG, BOSS_ADAPTERS;
  public static final ForgeConfigSpec.IntValue DURATION,
      COOLDOWN,
      CONTACT_GAP,
      OPEN_HITS,
      EDGE_INTERVAL,
      SWORD_INTERVAL,
      RING_INTERVAL,
      COUNTER_INTERVAL,
      CHAIN_INTERVAL,
      CLOAK_INTERVAL,
      RAIN_INTERVAL,
      RAIN_COUNT,
      MAX_TARGETS;
  public static final ForgeConfigSpec.DoubleValue RADIUS,
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
    DURATION = b.defineInRange("durationTicks", 220, 40, 400);
    COOLDOWN =
        b.comment("Starts when a domain opens; suspension never resets it.")
            .defineInRange("cooldownTicks", 320, 100, 2400);
    CONTACT_GAP = b.defineInRange("contactMaintainTicks", 40, 10, 100);
    OPEN_HITS = b.defineInRange("openingContacts", 3, 2, 10);
    EDGE_INTERVAL = b.defineInRange("afterimageInterval", 20, 10, 100);
    SWORD_INTERVAL = b.defineInRange("swordInterval", 30, 10, 200);
    RING_INTERVAL = b.defineInRange("ringInterval", 60, 20, 300);
    COUNTER_INTERVAL = b.defineInRange("counterInterval", 40, 20, 200);
    CHAIN_INTERVAL = b.defineInRange("chainInterval", 30, 20, 200);
    CLOAK_INTERVAL = b.defineInRange("cloakInterval", 60, 40, 300);
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
    DOMAIN_CAP = b.defineInRange("wholeDomainHP", 24d, 1d, 24d);
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
    CLIENT_SPEC = client.build();
  }

  private AkatsukiDomainConfig() {}
}
