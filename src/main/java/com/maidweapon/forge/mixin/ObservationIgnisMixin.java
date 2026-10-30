package com.maidweapon.forge.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.maidweapon.forge.system.akatsuki.provenance.DamageObservationService;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.*;

@Pseudo
@Mixin(
    targets = "com.github.L_Ender.cataclysm.entity.AnimationMonster.BossMonsters.Ignis_Entity",
    remap = false)
public abstract class ObservationIgnisMixin {
  @WrapMethod(
      method = {
        "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z",
        "m_6469_(Lnet/minecraft/world/damagesource/DamageSource;F)Z"
      },
      remap = false)
  private boolean cb$observe(DamageSource source, float amount, Operation<Boolean> original) {
    try (var scope =
        DamageObservationService.open(
            (LivingEntity) (Object) this,
            source,
            amount,
            "com.github.L_Ender.cataclysm.entity.AnimationMonster.BossMonsters.Ignis_Entity")) {
      try {
        boolean value = original.call(source, amount);
        scope.returned(value);
        return value;
      } catch (RuntimeException | Error e) {
        scope.failed();
        throw e;
      }
    }
  }
}
