package com.maidweapon.common;

import net.minecraftforge.common.ForgeConfigSpec;

/** Server-only soul policy. These values are not visual density or the old domain budget. */
public final class SoulDamageConfig {
  public enum Strategy {
    STANDARD,
    AGGRESSIVE,
    UNRESTRICTED_EXPERIMENTAL
  }

  public static final ForgeConfigSpec SPEC;
  public static final ForgeConfigSpec.EnumValue<Strategy> STRATEGY;
  public static final ForgeConfigSpec.BooleanValue EVENT_OVERRIDE, KNOWN_ADAPTERS, DIAGNOSTICS;
  public static final ForgeConfigSpec.DoubleValue CUT_MULTIPLIER,
      CAP_FACTOR,
      HIT_CEILING,
      TARGET_SECOND,
      PLAYER_SECOND,
      TARGET_WINDOW,
      PLAYER_WINDOW,
      BURST_MULTIPLIER,
      BURST_HEALTH_FRACTION,
      BURST_CEILING,
      LINK_RANGE;
  public static final ForgeConfigSpec.IntValue ANCHOR_LIFETIME,
      ANCHOR_INTERVAL,
      OWNER_ANCHORS,
      BURST_STAGES,
      BURST_DURATION,
      BURST_COOLDOWN,
      CUT_GAP;

  static {
    var b = new ForgeConfigSpec.Builder();
    STRATEGY = b.defineEnum("strategy", Strategy.AGGRESSIVE);
    EVENT_OVERRIDE = b.define("aggressiveEventOverride", true);
    KNOWN_ADAPTERS = b.define("knownBossAdapters", true);
    DIAGNOSTICS = b.define("damageDiagnostics", false);
    CUT_MULTIPLIER = b.defineInRange("spaceCutAttackMultiplier", 1.25, .1, 16);
    CAP_FACTOR = b.defineInRange("knownHitCapBreakthroughFactor", 1.75, 1, 8);
    HIT_CEILING = b.defineInRange("singleAttemptSafetyHP", 128d, 1, 4096);
    TARGET_SECOND = b.defineInRange("sharedTargetPer20TicksHP", 512d, 1, 8192);
    PLAYER_SECOND = b.defineInRange("playerPer20TicksHP", 384d, 1, 8192);
    TARGET_WINDOW = b.defineInRange("sharedTargetPer200TicksHP", 3072d, 1, 65536);
    PLAYER_WINDOW = b.defineInRange("playerPer200TicksHP", 2048d, 1, 65536);
    CUT_GAP = b.defineInRange("sharedTargetSpaceCutGapTicks", 4, 1, 40);
    BURST_MULTIPLIER = b.defineInRange("soulBurstAttackMultiplier", 5d, 1, 32);
    BURST_HEALTH_FRACTION = b.defineInRange("soulBurstMaxHealthFraction", .005, 0, .05);
    BURST_CEILING = b.defineInRange("soulBurstTotalSafetyHP", 512d, 1, 8192);
    BURST_STAGES = b.defineInRange("soulBurstStages", 4, 3, 5);
    BURST_DURATION = b.defineInRange("soulBurstDurationTicks", 10, 8, 12);
    BURST_COOLDOWN = b.defineInRange("sharedTargetBurstCooldownTicks", 60, 20, 400);
    ANCHOR_LIFETIME = b.defineInRange("soulAnchorLifetimeTicks", 60, 20, 200);
    ANCHOR_INTERVAL = b.defineInRange("soulAnchorCreationIntervalTicks", 60, 10, 400);
    OWNER_ANCHORS = b.defineInRange("maximumAnchorsPerPlayer", 4, 1, 8);
    LINK_RANGE = b.defineInRange("soulAnchorMaximumLinkRange", 128d, 16, 256);
    SPEC = b.build();
  }

  private SoulDamageConfig() {}
}
