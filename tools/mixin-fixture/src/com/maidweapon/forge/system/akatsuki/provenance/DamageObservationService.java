package com.maidweapon.forge.system.akatsuki.provenance;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/** Test sink for the unchanged production wrapper bodies, not the real observation service. */
public final class DamageObservationService {
  public static int opened, closed, failed, returned, armor, magic, depth;
  public static float armorIn, armorOut, magicIn, magicOut;

  public static Scope open(LivingEntity target, DamageSource source, float amount, String entry) {
    opened++;
    depth++;
    return new Scope();
  }

  public static final class Scope implements AutoCloseable {
    public void returned(boolean value) {
      if (value) returned++;
    }

    public void failed() {
      failed++;
    }

    public void close() {
      closed++;
      depth--;
    }
  }

  public static void defense(
      LivingEntity target, DamageSource source, float before, float after, boolean isArmor) {
    if (isArmor) {
      armor++;
      armorIn = before;
      armorOut = after;
    } else {
      magic++;
      magicIn = before;
      magicOut = after;
    }
  }
}
