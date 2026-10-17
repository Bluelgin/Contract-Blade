package com.maidweapon.forge.system.akatsuki.provenance;

import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.Level;

/**
 * Immutable evidence, never an entitlement inferred from an entire tick's HP delta. NaN means
 * unknown.
 */
public record DamageObservation(
    long sequence,
    UUID player,
    UUID target,
    int entityId,
    DamageProvenanceResolver.Proof proof,
    DamageSource source,
    MinecraftServer server,
    ResourceKey<Level> dimension,
    double raw,
    double effective,
    double actual,
    double before,
    double after,
    Defense defense,
    float absorptionAfter,
    double attack,
    double hurtInput,
    double hurtOutput,
    double damageInput,
    double damageOutput,
    String phaseBefore,
    String phaseAfter,
    Classification classification,
    String adapter,
    Confidence confidence,
    double settledAtCapture,
    boolean recoverable,
    boolean returned,
    long expires) {
  public record Defense(
      int armor, double toughness, int resistance, int enchantment, boolean blocking) {}

  public enum Confidence {
    VERIFIED_NATIVE_LIMIT,
    OBSERVED_NATIVE_LIMIT,
    UNKNOWN
  }

  public enum Classification {
    VERIFIED_SPECIAL_CAP,
    OBSERVED_SPECIAL_LIMIT,
    NORMAL_DEFENSE,
    ABSORPTION,
    PROTECTED_PHASE,
    UNVERIFIED_REJECTION,
    CUSTOM_HEALTH_UNKNOWN,
    UNSUPPORTED_SOURCE,
    AMBIGUOUS_CHANGE
  }

  public double loss() {
    return recoverable && Double.isFinite(effective) ? Math.max(0, effective - actual) : 0;
  }
}
