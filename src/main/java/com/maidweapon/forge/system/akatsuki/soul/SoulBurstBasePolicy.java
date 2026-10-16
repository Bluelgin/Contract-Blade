package com.maidweapon.forge.system.akatsuki.soul;

/** Independent skill damage. No inferred debt, fixed HP budget, or direct-health write. */
public final class SoulBurstBasePolicy {
  public static double requested(double attack, double maxHealth, int level,
      double multiplier, double healthFraction) {
    if (!Double.isFinite(attack) || !Double.isFinite(maxHealth)
        || !Double.isFinite(multiplier) || !Double.isFinite(healthFraction)
        || attack < 0 || maxHealth <= 0 || multiplier < 0 || healthFraction < 0 || level < 2)
      return 0;
    double growth = .5 + .05 * Math.min(10, level);
    double result = (attack * multiplier + maxHealth * healthFraction) * growth;
    return Math.min(Float.MAX_VALUE, result);
  }

  private SoulBurstBasePolicy() {}
}
