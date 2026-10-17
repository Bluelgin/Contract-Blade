package com.maidweapon.forge.system.akatsuki.soul;

/**
 * Normal cuts never mint recovery entitlement. Cap zero is deliberately unbounded at max level.
 */
public final class SoulNormalCutPolicy {
  public static double requested(double attack, double multiplier, double levelCap) {
    if (!Double.isFinite(attack)
        || !Double.isFinite(multiplier)
        || !Double.isFinite(levelCap)
        || attack <= 0
        || multiplier <= 0
        || levelCap < 0) return 0;
    double scaled = attack * multiplier;
    // The Minecraft hurt API accepts float: guard only numerical overflow.
    if (!Double.isFinite(scaled)) scaled = Float.MAX_VALUE / 4d;
    scaled = Math.min(scaled, Float.MAX_VALUE / 4d);
    return levelCap == 0 ? scaled : Math.min(scaled, levelCap);
  }

  /** A deliberately weaker unknown-target probe; no flat HP upper limit. */
  public static double unknownPressure(double requested, double levelRate) {
    if (!Double.isFinite(requested) || requested <= 0
        || !Double.isFinite(levelRate) || levelRate <= 0) return 0;
    return Math.min(requested, Math.max(1, requested * Math.min(.12, levelRate)));
  }

  private SoulNormalCutPolicy() {}
}
