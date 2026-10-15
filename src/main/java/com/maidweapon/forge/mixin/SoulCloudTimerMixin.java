package com.maidweapon.forge.mixin;

import com.maidweapon.forge.compat.DomainTargetCompatibility;
import com.maidweapon.forge.system.akatsuki.soul.SoulStrikeContext;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

/** Read override rather than a shared field mutation: nested unrelated attacks retain the timer. */
@Pseudo
@Mixin(
    targets =
        "net.miauczel.legendary_monsters.entity.AnimatedMonster.IAnimatedBoss.CloudGolem.Cloud_GolemEntity",
    remap = false)
public abstract class SoulCloudTimerMixin {
  @Shadow public int GolemInvulnerabilityTime;

  @Redirect(
      method = {
        "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z",
        "m_6469_(Lnet/minecraft/world/damagesource/DamageSource;F)Z"
      },
      at =
          @At(
              value = "FIELD",
              target =
                  "Lnet/miauczel/legendary_monsters/entity/AnimatedMonster/IAnimatedBoss/CloudGolem/Cloud_GolemEntity;GolemInvulnerabilityTime:I",
              opcode = 180),
      require = 1)
  private int soulTimer(@Coerce Object self, DamageSource source, float amount) {
    var c = SoulStrikeContext.current();
    return c != null
            && c.strong
            && c.matches((LivingEntity) self, source)
            && DomainTargetCompatibility.verified((LivingEntity) self)
        ? 0
        : GolemInvulnerabilityTime;
  }
}
