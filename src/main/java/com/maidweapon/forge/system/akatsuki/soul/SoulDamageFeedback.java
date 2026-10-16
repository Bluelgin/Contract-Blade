package com.maidweapon.forge.system.akatsuki.soul;

/** Evidence from one real call, including rejected requests. No Boolean hurt() success shortcut. */
public record SoulDamageFeedback(
    long sequence,
    SoulDamagePolicy.Mode mode,
    String channel,
    double requested,
    float before,
    float after,
    boolean returned,
    boolean attempted,
    float attack,
    float hurtInput,
    float hurtFinal,
    float damageInput,
    float damageFinal,
    int cancellations,
    int reductions,
    boolean compensated,
    String phaseBefore,
    String phaseAfter,
    String reason) {
  public double loss() {
    return Float.isFinite(before - after) ? Math.max(0, before - after) : 0;
  }

  public boolean success() {
    return Float.isFinite(before - after)
        && before > after
        && before - after <= requested * 1.05 + .01;
  }
}
