package com.maidweapon.forge.system.akatsuki.soul;

/** Conserves all validated finite debt across 3–5 weighted stages; no HP ceiling. */
public final class SoulStageAllocation {
  public static double[] split(double total, int stages) {
    if (!Double.isFinite(total) || total < 0 || total > Float.MAX_VALUE
        || stages < 3 || stages > 5) throw new IllegalArgumentException();
    var result = new double[stages];
    double weights = stages * (stages + 1) / 2d;
    double used = 0;
    for (int i = 0; i < stages - 1; i++) {
      result[i] = total * (i + 1) / weights;
      used += result[i];
    }
    result[stages - 1] = Math.max(0, total - used);
    return result;
  }
  private SoulStageAllocation() {}
}
