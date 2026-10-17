package com.maidweapon.forge.system.akatsuki.soul;

/**
 * Explicit per-strike permission boundaries. Uncredited spatial cuts may scale
 * with attack, but never inherit anti-boss-limit capabilities from the strategy.
 * A validated debt reservation is the only route to cap/phase override authority.
 */
public final class SoulStrikePermissions {
  public static boolean overrideEvents(boolean recovery, boolean aggressive, boolean enabled) {
    return recovery && aggressive && enabled;
  }

  public static boolean strong(boolean recovery, boolean aggressive) {
    return recovery && aggressive;
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
          recovery || mode == SoulDamagePolicy.Mode.SOUL_BURST;
      case "bypasses_invulnerability", "cataclysm:bypasses_hurt_time" ->
          recovery && strong;
      default -> false;
    };
  }

  private SoulStrikePermissions() {}
}
