package com.maidweapon.forge.system.akatsuki.soul;

/**
 * Native-hit resonance uses observed actual HP loss only, never the denied
 * pre-cap theoretical amount. It is not a recoverable receipt and cannot refill debt.
 */
public final class SoulResonancePolicy {
  public static double requested(double actualHpLoss, double ratio) {
    if (!Double.isFinite(actualHpLoss) || !Double.isFinite(ratio)
        || actualHpLoss <= 0 || ratio <= 0 || ratio > .8) return 0;
    return Math.max(0, Math.min((double) Float.MAX_VALUE / 4, actualHpLoss * ratio));
  }

  private SoulResonancePolicy() {}
}
