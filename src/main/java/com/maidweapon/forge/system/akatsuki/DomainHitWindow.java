package com.maidweapon.forge.system.akatsuki;

import com.maidweapon.forge.compat.CompatDiagnostics;
import java.lang.reflect.Field;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

/**
 * Restores only hit bookkeeping produced by our own synchronous pulse, never clears an existing
 * cooldown.
 */
public final class DomainHitWindow implements AutoCloseable {
  private static final Field LAST_HURT = lastHurt();
  private final LivingEntity target;
  private final int invulnerable, hurt, duration;
  private final float last;

  private DomainHitWindow(LivingEntity target) throws IllegalAccessException {
    this.target = target;
    invulnerable = target.invulnerableTime;
    hurt = target.hurtTime;
    duration = target.hurtDuration;
    last = LAST_HURT.getFloat(target);
  }

  private static Field lastHurt() {
    try {
      return ObfuscationReflectionHelper.findField(LivingEntity.class, "f_20898_");
    } catch (RuntimeException failure) {
      CompatDiagnostics.warnOnce("domain-native-hit-window", failure);
      return null;
    }
  }

  public static DomainHitWindow capture(LivingEntity target) {
    if (LAST_HURT == null) return null;
    try {
      return new DomainHitWindow(target);
    } catch (IllegalAccessException failure) {
      CompatDiagnostics.warnOnce("domain-native-hit-window", failure);
      return null;
    }
  }

  @Override
  public void close() {
    target.invulnerableTime = invulnerable;
    target.hurtTime = hurt;
    target.hurtDuration = duration;
    try {
      LAST_HURT.setFloat(target, last);
    } catch (IllegalAccessException failure) {
      CompatDiagnostics.warnOnce("domain-native-hit-window-restore", failure);
    }
  }
}
