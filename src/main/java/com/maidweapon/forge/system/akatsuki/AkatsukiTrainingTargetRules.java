package com.maidweapon.forge.system.akatsuki;

import java.util.Collection;
import java.util.Locale;

/** Exact registry IDs only; the caller additionally verifies owner-directed contact. */
public final class AkatsukiTrainingTargetRules {
  public static boolean matches(String entityTypeId, Collection<? extends String> extras) {
    if (entityTypeId == null || !entityTypeId.contains(":")) return false;
    if (extras != null && extras.contains(entityTypeId)) return true;
    String path = entityTypeId.substring(entityTypeId.indexOf(':') + 1).toLowerCase(Locale.ROOT);
    for (String part : path.split("[_./-]"))
      if (part.equals("dummy") || part.equals("mannequin")) return true;
    return false;
  }

  private AkatsukiTrainingTargetRules() {}
}
