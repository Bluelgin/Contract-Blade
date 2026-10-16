package com.maidweapon.forge.system.akatsuki.soul;

/** Waterfill weighted stages before reserving safety; no clipped, permanently unusable tail. */
public final class SoulStageAllocation {
  public static double[] split(double total, int stages, double cap) {
    if (!Double.isFinite(total)
        || !Double.isFinite(cap)
        || total < 0
        || cap <= 0
        || stages < 3
        || stages > 5) throw new IllegalArgumentException();
    var a = new double[stages];
    double left = Math.min(total, stages * cap);
    for (int round = 0; round <= stages && left > .000001; round++) {
      double weights = 0;
      for (int i = 0; i < stages; i++) if (a[i] < cap - .000001) weights += i + 1;
      double spent = 0;
      for (int i = 0; i < stages; i++)
        if (a[i] < cap - .000001) {
          double n = Math.min(cap - a[i], left * (i + 1) / weights);
          a[i] += n;
          spent += n;
        }
      left -= spent;
    }
    return a;
  }

  private SoulStageAllocation() {}
}
