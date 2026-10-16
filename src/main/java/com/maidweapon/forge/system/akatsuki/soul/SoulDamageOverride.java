package com.maidweapon.forge.system.akatsuki.soul;

import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

/**
 * Hooks operate on the exact in-flight source and victim, never on weapon names or a global flag.
 */
@Mod.EventBusSubscriber(modid = "maid_weapon")
public final class SoulDamageOverride {
  public static SoulStrikeContext context(Object event) {
    var c = SoulStrikeContext.current();
    if (c == null) return null;
    if (event instanceof LivingAttackEvent e)
      return c.matches(e.getEntity(), e.getSource()) ? c : null;
    if (event instanceof LivingHurtEvent e)
      return c.matches(e.getEntity(), e.getSource()) ? c : null;
    if (event instanceof LivingDamageEvent e)
      return c.matches(e.getEntity(), e.getSource()) ? c : null;
    return null;
  }

  public static boolean rejectCancellation(Object event, boolean cancel) {
    var c = context(event);
    if (c == null || !c.events || !cancel) return false;
    c.cancellations++;
    return true;
  }

  public static boolean uncancelable(Object event) {
    var c = context(event);
    return c != null && c.events;
  }

  public static float amount(Object event, float amount, boolean constructor) {
    var c = context(event);
    if (c == null) return amount;
    if (constructor) {
      if (event instanceof LivingHurtEvent) c.hurtInput = amount;
      else c.damageInput = amount;
    }
    float result = !Float.isFinite(amount) ? 0 : Math.max(0, Math.min(c.requested, amount));
    if (c.events && result < c.requested) {
      c.reductions++;
      result = c.requested;
    }
    return result;
  }

  @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
  public static void attack(LivingAttackEvent e) {
    var c = context(e);
    if (c != null) c.attack = e.getAmount();
  }

  @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
  public static void hurt(LivingHurtEvent e) {
    var c = context(e);
    if (c != null) c.hurtFinal = e.getAmount();
  }

  @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
  public static void damage(LivingDamageEvent e) {
    var c = context(e);
    if (c != null) c.damageFinal = e.getAmount();
  }

  private SoulDamageOverride() {}
}
