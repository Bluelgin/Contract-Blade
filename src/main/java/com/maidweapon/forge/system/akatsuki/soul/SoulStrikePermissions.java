package com.maidweapon.forge.system.akatsuki.soul;

/**
 * Explicit per-strike permission boundaries. Uncredited spatial cuts may scale
 * with attack, but never inherit anti-boss-limit capabilities from the strategy.
 * A validated debt reservation is the only route to cap/phase override authority.
 */
public final class SoulStrikePermissions {
  public static boolean overrideEvents(boolean recovery, boolean aggressive, boolean enabled) {
    return overrideEvents(recovery, aggressive, enabled, false);
  }

  public static boolean overrideEvents(boolean recovery, boolean aggressive, boolean enabled,
      boolean unrecognizedHostile) {
    return (recovery || unrecognizedHostile) && aggressive && enabled;
  }

  public static boolean strong(boolean recovery, boolean aggressive) {
    return strong(recovery, aggressive, false);
  }

  public static boolean strong(boolean recovery, boolean aggressive, boolean unrecognizedHostile) {
    return aggressive && (recovery || unrecognizedHostile);
  }

  /** Only modded, genuinely hostile, unrecognized targets get experimental strong cuts.
   *  This is not a boss registry; registered adapters always take precedence.
   */
  public static boolean unrecognizedHostile(boolean verifiedAdapter, boolean hostile,
      String entityNamespace) {
    return !verifiedAdapter && hostile && entityNamespace != null
        && !entityNamespace.equals("minecraft");
  }

  /**
   * A normal critical is paid from a post-mitigation native HP loss: preserve that
   * amount against duplicate vanilla armor/effects and post-hit cooldown, but do not
   * override special boss invulnerability or the scoped Boss adapters.
   */
  public static boolean bypassTag(
      SoulDamagePolicy.Mode mode, boolean recovery, boolean strong, String tag) {
    if (mode == null || tag == null) return false;
    return switch (tag) {
      case "bypasses_armor", "bypasses_resistance", "bypasses_enchantments",
          "bypasses_effects", "bypasses_shield", "bypasses_cooldown" ->
          recovery || mode == SoulDamagePolicy.Mode.SOUL_BURST
              || mode == SoulDamagePolicy.Mode.SPACE_CUT && strong;
      case "bypasses_invulnerability", "cataclysm:bypasses_hurt_time" ->
          strong;
      default -> false;
    };
  }

  private SoulStrikePermissions() {}
}
