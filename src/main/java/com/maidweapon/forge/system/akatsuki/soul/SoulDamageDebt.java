package com.maidweapon.forge.system.akatsuki.soul;

/** One attack's finite confirmed deficit; cut and core consume the same cumulative entitlement. */
public final class SoulDamageDebt {
  private final double loss;
  private double reserved, recovered, attempted;
  private boolean ordinaryClaimed, aerialClaimed, invalid;

  public SoulDamageDebt(double effective, double actual) {
    if (!Double.isFinite(effective) || !Double.isFinite(actual) || actual < 0 || effective < actual)
      throw new IllegalArgumentException("Invalid native receipt");
    loss = effective - actual;
  }

  public double available(double rate, boolean aerial) {
    return invalid
            || !Double.isFinite(rate)
            || rate < 0
            || rate > 1
            || (aerial ? aerialClaimed : ordinaryClaimed)
        ? 0
        : Math.max(0, loss * rate - recovered - reserved);
  }

  public double reserve(double rate, double maximum, boolean aerial) {
    if (!Double.isFinite(maximum) || maximum <= 0) return 0;
    double n = Math.min(maximum, available(rate, aerial));
    if (n > 0) {
      reserved += n;
      if (aerial) aerialClaimed = true;
      else ordinaryClaimed = true;
    }
    return n;
  }

  /** One failed star-chain permanently forfeits aerial recovery from this receipt.
   *  The ordinary small cut claim remains independent.
   */
  public void forfeitAerial() {
    aerialClaimed = true;
  }

  public void settle(double allocation, double actual, boolean dispatched) {
    if (!Double.isFinite(allocation)
        || !Double.isFinite(actual)
        || allocation < 0
        || actual < 0
        || allocation > reserved + .0001)
      throw new IllegalArgumentException("Duplicate settlement");
    reserved = Math.max(0, reserved - allocation);
    if (dispatched) attempted += allocation;
    recovered += Math.min(actual, allocation);
    if (actual > allocation + .02) invalid = true;
  }

  public void abandon(double n) {
    if (!Double.isFinite(n) || n < 0 || n > reserved + .0001)
      throw new IllegalArgumentException("Invalid abandonment");
    reserved = Math.max(0, reserved - n);
  }

  public double reserved() {
    return reserved;
  }

  public double recovered() {
    return recovered;
  }

  public double attempted() {
    return attempted;
  }

  public boolean invalid() {
    return invalid;
  }
}
