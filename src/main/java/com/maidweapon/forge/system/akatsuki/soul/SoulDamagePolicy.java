package com.maidweapon.forge.system.akatsuki.soul;

/** Pure requested-damage policy. Native caps are a breakthrough reference, never a ceiling. */
public final class SoulDamagePolicy {
  public enum Mode {
    SPACE_CUT,
    SOUL_BURST
  }

  public record Inputs(
      double attack,
      double maximumHealth,
      double nativeHitCap,
      int level,
      double cutMultiplier,
      double breakthroughFactor,
      double burstMultiplier,
      double healthFraction,
      double attemptCeiling,
      double burstCeiling,
      double strategyScale) {}

  public static double requested(Mode mode, Inputs p) {
    if (!Double.isFinite(p.attack())
        || p.attack() <= 0
        || !Double.isFinite(p.maximumHealth())
        || p.maximumHealth() <= 0) return 0;
    double attack = Math.min(1024, p.attack());
    double growth = 1 + Math.max(0, Math.min(10, p.level()) - 1) * .045;
    double cut = attack * p.cutMultiplier() * growth;
    if (Double.isFinite(p.nativeHitCap()) && p.nativeHitCap() > 0)
      cut = Math.max(cut, Math.min(p.nativeHitCap() * p.breakthroughFactor(), attack * 8));
    double value =
        mode == Mode.SPACE_CUT
            ? cut
            : Math.max(
                cut * 2,
                attack * p.burstMultiplier() * growth
                    + Math.min(p.maximumHealth() * p.healthFraction(), attack * 12));
    return finiteCeiling(
        value * p.strategyScale(), mode == Mode.SPACE_CUT ? p.attemptCeiling() : p.burstCeiling());
  }

  private static double finiteCeiling(double amount, double ceiling) {
    return Double.isFinite(amount) && Double.isFinite(ceiling) && ceiling > 0
        ? Math.max(0, Math.min(amount, ceiling))
        : 0;
  }

  /** Unequal stages keep the finisher meaningful while preserving exactly one reserved total. */
  public static double stage(double total, int index, int count) {
    if (count < 3 || count > 5 || index < 0 || index >= count || !Double.isFinite(total)) return 0;
    double denominator = count * (count + 1d) / 2;
    return Math.max(0, total) * (index + 1) / denominator;
  }

  private SoulDamagePolicy() {}
}
