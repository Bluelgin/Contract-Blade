package com.maidweapon.forge.system.akatsuki.soul;

public final class SoulRecoveryPolicy {
  private static final double[] CUT = {.02, .03, .04, .05, .06, .07, .08, .09, .10, .12},
      AIR = {0, .30, .35, .40, .50, .60, .70, .80, .90, 1};

  public static double cut(int level) {
    return CUT[Math.max(0, Math.min(9, level - 1))];
  }

  public static double aerial(int level) {
    return AIR[Math.max(0, Math.min(9, level - 1))];
  }

  private SoulRecoveryPolicy() {}
}
