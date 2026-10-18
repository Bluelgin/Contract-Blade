package com.maidweapon.forge.system.akatsuki.provenance;

/**
 * Conservative, boss-agnostic limit evidence. Captures only a reduction inside
 * ONE trusted, exact-source native hurt call with a complete, unmodified downstream
 * Forge/vanilla health pipeline. Never guesses theoretical HP from no-op attacks.
 */
public final class GenericSoulLimitEvidence {
  private static boolean same(double a, double b) {
    return Double.isFinite(a) && Double.isFinite(b)
        && Math.abs(a - b) <= Math.max(.015, Math.abs(a) * .0001);
  }

  public static boolean confirmed(double offered, double observed, double hurtIn,
      double hurtOut, double armorIn, double armorOut, double magicIn,
      double magicOut, double damageIn, double damageOut, double actual) {
    if (!Double.isFinite(offered) || offered <= 0 || !Double.isFinite(observed)
        || observed <= 0 || !Double.isFinite(actual) || actual <= 0
        || !Double.isFinite(hurtIn) || !Double.isFinite(hurtOut)
        || !Double.isFinite(armorIn) || !Double.isFinite(armorOut)
        || !Double.isFinite(magicIn) || !Double.isFinite(magicOut)
        || !Double.isFinite(damageIn) || !Double.isFinite(damageOut)
        || hurtOut <= 0 || damageOut < 0 || offered + .015 < observed
        || !same(armorIn, hurtOut) || !same(magicIn, armorOut)
        || damageIn + .015 < damageOut) return false;
    // Inherited, ordinary armor/magic mitigation is checked separately in the
    // observation service. A limit must occur BEFORE those vanilla reductions.
    boolean nativeClamped = offered > observed + .015
        || observed > hurtIn + .015;
    boolean eventClamped = hurtIn > hurtOut + .015;
    return nativeClamped || eventClamped;
  }

  private GenericSoulLimitEvidence() {}
}
