package com.maidweapon.forge.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.maidweapon.forge.system.akatsuki.provenance.DamageObservationService;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "com.Polarice3.Goety.common.entities.boss.Apostle", remap = false)
public abstract class ObservationApostleMixin {
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
            "com.Polarice3.Goety.common.entities.boss.Apostle")) {
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

  @WrapOperation(
      method = {
        "actuallyHurt(Lnet/minecraft/world/damagesource/DamageSource;F)V",
        "m_6475_(Lnet/minecraft/world/damagesource/DamageSource;F)V"
      },
      at = @At(value = "INVOKE", target = "Ljava/lang/Math;min(FF)F"),
      require = 1)
  private float cb$cap(float a, float b, Operation<Float> original) {
    float result = original.call(a, b);
    DamageObservationService.capped((float) a, (float) b, (float) result);
    return result;
  }
}
