package com.maidweapon.forge.compat;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/** Optional capabilities on the existing domain adapter registry; no parallel boss registry. */
public interface BossLimitAdapter {
  default boolean compensate(LivingEntity target, DamageSource source, float deficit)
      throws ReflectiveOperationException {
    return false;
  }

  default boolean initializing(LivingEntity target) throws ReflectiveOperationException {
    return false;
  }
}
