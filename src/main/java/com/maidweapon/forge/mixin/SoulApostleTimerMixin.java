package com.maidweapon.forge.mixin;

import com.maidweapon.forge.compat.DomainTargetCompatibility;
import com.maidweapon.forge.system.akatsuki.soul.SoulStrikeContext;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

/** Goety 2.5.58.4 has two rejects before Forge and another moddedInvul check in actuallyHurt. */
@Pseudo
@Mixin(targets = "com.Polarice3.Goety.common.entities.boss.Apostle", remap = false)
public abstract class SoulApostleTimerMixin {
  @Shadow public int moddedInvul;
  @Shadow public int obsidianInvul;

  private boolean soulAllowed(Object self, DamageSource source) {
    var c = SoulStrikeContext.current();
    return c != null
        && c.strong
        && c.matches((LivingEntity) self, source)
        && DomainTargetCompatibility.verified((LivingEntity) self);
  }

  @Redirect(
      method = {
        "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z",
        "m_6469_(Lnet/minecraft/world/damagesource/DamageSource;F)Z",
        "actuallyHurt(Lnet/minecraft/world/damagesource/DamageSource;F)V",
        "m_6475_(Lnet/minecraft/world/damagesource/DamageSource;F)V"
      },
      at =
          @At(
              value = "FIELD",
              target = "Lcom/Polarice3/Goety/common/entities/boss/Apostle;moddedInvul:I",
              opcode = 180),
      require = 1)
  private int soulModded(@Coerce Object self, DamageSource source, float amount) {
    return soulAllowed(self, source) ? 0 : moddedInvul;
  }

  @Redirect(
      method = {
        "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z",
        "m_6469_(Lnet/minecraft/world/damagesource/DamageSource;F)Z"
      },
      at =
          @At(
              value = "FIELD",
              target = "Lcom/Polarice3/Goety/common/entities/boss/Apostle;obsidianInvul:I",
              opcode = 180),
      require = 1)
  private int soulObsidian(@Coerce Object self, DamageSource source, float amount) {
    return soulAllowed(self, source) ? 0 : obsidianInvul;
  }
}
