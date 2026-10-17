package com.maidweapon.forge.system.akatsuki.soul;

/** A native hit authorizes a core opportunity, never a recovery receipt. */
public final class SoulAnchorTriggerPolicy {
  public static boolean eligible(boolean issued, boolean currentCarrier, double raw, String kind) {
    return issued && currentCarrier && Double.isFinite(raw) && raw > 0
        && kind != null && (kind.equals("VERIFIED_SPECIAL_CAP") || kind.equals("NORMAL_DEFENSE") || kind.equals("ABSORPTION"));
  }

  private SoulAnchorTriggerPolicy() {}
}
