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
  public static final ForgeConfigSpec.BooleanValue EVENT_OVERRIDE, KNOWN_ADAPTERS, DIAGNOSTICS,
      CREATIVE_DAMAGE, TRAINING_TARGETS;
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
      LINK_RANGE,
      BASE_CUT_FRACTION,
      BASE_CUT_MAXIMUM,
      ANCHOR_HEAD_OFFSET,
      ANCHOR_MIN_HEIGHT,
      ANCHOR_MAX_HEIGHT;
  public static final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> EXTRA_TRAINING_TYPES;
  public static final ForgeConfigSpec.IntValue ANCHOR_LIFETIME,
      ANCHOR_INTERVAL,
      OWNER_ANCHORS,
      BURST_STAGES,
      BURST_DURATION,
      BURST_COOLDOWN,
      CUT_GAP,
      RECOVERY_GAP;

  static {
    var b = new ForgeConfigSpec.Builder();
    STRATEGY = b.defineEnum("strategy", Strategy.AGGRESSIVE);
    EVENT_OVERRIDE = b.define("aggressiveEventOverride", true);
    KNOWN_ADAPTERS = b.define("knownBossAdapters", true);
    DIAGNOSTICS = b.define("damageDiagnostics", false);
    CREATIVE_DAMAGE =
        b.comment("Allow valid awakened Akatsuki owners to deal domain damage in Creative.")
            .define("allowCreativeDomainDamage", true);
    TRAINING_TARGETS =
        b.comment("Allow explicitly struck dummy entities; no friendly area scans.")
            .define("allowTrainingDummyTargets", true);
    EXTRA_TRAINING_TYPES =
        b.comment("Additional exact namespace:path entity IDs treated as training targets.")
            .defineListAllowEmpty(
                "additionalTrainingEntityTypes",
                java.util.List.of(),
                v -> v instanceof String s
                    && s.contains(":")
                    && net.minecraft.resources.ResourceLocation.tryParse(s) != null);
    // DEPRECATED independent request keys retained for config compatibility; not recovery
    // entitlements.
    CUT_MULTIPLIER = b.defineInRange("spaceCutAttackMultiplier", 1.25, .1, 16);
    CAP_FACTOR = b.defineInRange("knownHitCapBreakthroughFactor", 1.75, 1, 8);
    HIT_CEILING = b.comment("Deprecated compatibility key; no longer limits live Akatsuki HP.")
        .defineInRange("singleAttemptSafetyHP", 128d, 1, 4096);
    TARGET_SECOND = b.comment("Deprecated compatibility key; no longer limits live Akatsuki HP.")
        .defineInRange("sharedTargetPer20TicksHP", 512d, 1, 8192);
    PLAYER_SECOND = b.comment("Deprecated compatibility key; no longer limits live Akatsuki HP.")
        .defineInRange("playerPer20TicksHP", 384d, 1, 8192);
    TARGET_WINDOW = b.comment("Deprecated compatibility key; no longer limits live Akatsuki HP.")
        .defineInRange("sharedTargetPer200TicksHP", 3072d, 1, 65536);
    PLAYER_WINDOW = b.comment("Deprecated compatibility key; no longer limits live Akatsuki HP.")
        .defineInRange("playerPer200TicksHP", 2048d, 1, 65536);
    CUT_GAP = b.defineInRange("sharedTargetSpaceCutGapTicks", 4, 1, 40);
    BURST_MULTIPLIER = b.comment("Deprecated: no longer adds independent soul-core damage.")
        .defineInRange("soulBurstAttackMultiplier", 5d, 1, 32);
    BURST_HEALTH_FRACTION = b.comment("Deprecated: no longer adds independent soul-core damage.")
        .defineInRange("soulBurstMaxHealthFraction", .005, 0, .05);
    BURST_CEILING = b.comment("Deprecated compatibility key; no longer limits live Akatsuki HP.")
        .defineInRange("soulBurstTotalSafetyHP", 512d, 1, 8192);
    BURST_STAGES = b.defineInRange("soulBurstStages", 4, 3, 5);
    BURST_DURATION = b.defineInRange("soulBurstDurationTicks", 10, 8, 12);
    BURST_COOLDOWN = b.defineInRange("sharedTargetBurstCooldownTicks", 60, 20, 400);
    ANCHOR_LIFETIME = b.defineInRange("soulAnchorLifetimeTicks", 90, 20, 200);
    ANCHOR_INTERVAL = b.defineInRange("soulAnchorCreationIntervalTicks", 60, 10, 400);
    OWNER_ANCHORS = b.defineInRange("maximumAnchorsPerPlayer", 4, 1, 8);
    LINK_RANGE = b.defineInRange("soulAnchorMaximumLinkRange", 128d, 16, 256);
    RECOVERY_GAP = b.defineInRange("sharedTargetRecoveryGapTicks", 12, 1, 100);
    BASE_CUT_FRACTION = b.defineInRange("baseSpaceCutAttackFraction", .05, 0, 1);
    BASE_CUT_MAXIMUM = b.defineInRange("baseSpaceCutMaximumHP", .5, 0, 8);
    ANCHOR_HEAD_OFFSET = b.comment("Vertical gap above the boss hitbox; summoned swords can hit from below.")
        .defineInRange("aerialAnchorHeadOffset", 5d, 4, 10);
    ANCHOR_MIN_HEIGHT = b.comment("Minimum anchor altitude above target feet, if higher than head clearance.")
        .defineInRange("aerialAnchorMinimumHeight", 6d, 4, 12);
    ANCHOR_MAX_HEIGHT = b.comment("Legacy cap above target feet; tall bosses still retain >=4 blocks head clearance.")
        .defineInRange("aerialAnchorGiantSideHeight", 18d, 8, 36);
    SPEC = b.build();
  }

  private SoulDamageConfig() {}
}
