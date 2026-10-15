package com.maidweapon.forge.system.akatsuki;

import com.maidweapon.forge.system.akatsuki.soul.SoulDamageEngine;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/** Compatibility entry point; all live damage uses the shared soul engine. */
public final class AkatsukiDomainDamage {
  public static boolean applying() {
    return SoulDamageEngine.applying();
  }

  public static boolean domain(DamageSource source) {
    return applying()
        || source
            .typeHolder()
            .unwrapKey()
            .map(
                k ->
                    k.location().getNamespace().equals("maid_weapon")
                        && k.location().getPath().startsWith("akatsuki_domain_"))
            .orElse(false);
  }

  public static void pulse(
      ServerPlayer player, LivingEntity target, AkatsukiDomainSession session, boolean adaptive) {
    SoulDamageEngine.cut(player, target, session);
  }

  public static void prune(long now) {
    SoulDamageEngine.prune(now);
  }

  public static void forget(LivingEntity target) {
    SoulDamageEngine.forget(target);
  }

  public static void clear() {
    SoulDamageEngine.clear();
  }

  private AkatsukiDomainDamage() {}
}
