package com.maidweapon.forge.system.akatsuki.soul;

import java.util.UUID;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/** Exact source-object capability, installed only by the authorized engine on the server thread. */
public final class SoulStrikeContext implements AutoCloseable {
  private static final ThreadLocal<SoulStrikeContext> CURRENT = new ThreadLocal<>();
  public final UUID attacker, target;
  public final String binding;
  public final long sequence;
  public final SoulDamagePolicy.Mode mode;
  public final float requested;
  public final boolean recovery, events, strong, experimental;
  private final LivingEntity victim;
  private final DamageSource source;
  private boolean closed;
  public float attack = Float.NaN,
      hurtInput = Float.NaN,
      hurtFinal = Float.NaN,
      damageInput = Float.NaN,
      damageFinal = Float.NaN;
  public int cancellations, reductions;
  public boolean compensated;

  SoulStrikeContext(
      UUID attacker,
      LivingEntity target,
      String binding,
      long sequence,
      SoulDamagePolicy.Mode mode,
      DamageSource source,
      float requested,
      boolean recovery,
      boolean events,
      boolean strong,
      boolean experimental) {
    if (CURRENT.get() != null
        || target.level().isClientSide()
        || !target.getServer().isSameThread()
        || !Float.isFinite(requested)
        || requested <= 0)
      throw new IllegalStateException("Invalid or recursive soul strike scope");
    this.attacker = attacker;
    this.target = target.getUUID();
    this.victim = target;
    this.binding = binding;
    this.sequence = sequence;
    this.mode = mode;
    this.source = source;
    this.requested = requested;
    this.recovery = recovery;
    this.events = events;
    this.strong = strong;
    this.experimental = experimental;
    CURRENT.set(this);
  }

  public static SoulStrikeContext current() {
    return CURRENT.get();
  }

  public boolean matches(LivingEntity target, DamageSource source) {
    return !closed
        && !target.isRemoved()
        && victim == target
        && this.source == source
        && target.getServer() != null
        && target.getServer().isSameThread();
  }

  public boolean matches(DamageSource source) {
    return matches(victim, source);
  }

  @Override
  public void close() {
    if (!closed) {
      closed = true;
      if (CURRENT.get() == this) CURRENT.remove();
    }
  }

  public static void clearThread() {
    CURRENT.remove();
  }
}
