package com.maidweapon.forge.client;

/** Pure cosmetic cadence; never used to schedule hurt calls. Activity decays after the last hit. */
public final class AkatsukiVisualDirector {
  private long lastAttack = Long.MIN_VALUE / 2, nextCut, nextSwords;
  private int combo;

  public void attack(long now) {
    combo = now - lastAttack <= 12 ? Math.min(8, combo + 1) : 1;
    lastAttack = now;
  }

  public double intensity(long now) {
    return Math.max(0, 1 - Math.max(0, now - lastAttack - 8) / 32d) * (1 + combo / 8d);
  }

  public int cuts(long now, boolean rampage) {
    double power = intensity(now);
    if (power <= 0 || now < nextCut) return 0;
    nextCut = now + (rampage ? (combo >= 4 ? 1 : 2) : (combo >= 4 ? 2 : 3));
    return rampage ? Math.min(5, 2 + combo / 2) : Math.min(3, 1 + combo / 3);
  }

  public boolean swords(long now, boolean rampage) {
    if (intensity(now) <= 0 || now < nextSwords) return false;
    nextSwords = now + (rampage ? (combo >= 4 ? 4 : 6) : 8);
    return true;
  }
}
