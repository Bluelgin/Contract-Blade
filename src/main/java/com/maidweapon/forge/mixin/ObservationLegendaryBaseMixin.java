package com.maidweapon.forge.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.maidweapon.forge.system.akatsuki.provenance.DamageObservationService;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(
    targets = "net.miauczel.legendary_monsters.entity.AnimatedMonster.OriginClasses.IAnimatedBoss",
    remap = false)
public abstract class ObservationLegendaryBaseMixin {
  @WrapMethod(
      method = {"hurt", "m_6469_"},
      remap = false)
  private boolean cb$observe(DamageSource source, float amount, Operation<Boolean> original) {
    try (var scope =
        DamageObservationService.open(
            (LivingEntity) (Object) this,
            source,
            amount,
            "net.miauczel.legendary_monsters.entity.AnimatedMonster.OriginClasses.IAnimatedBoss")) {
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
      method = {"hurt", "m_6469_"},
      at = @At(value = "INVOKE", target = "Ljava/lang/Math;min(DD)D"),
      require = 1)
  private double cb$cap(double a, double b, Operation<Double> original) {
    double result = original.call(a, b);
    DamageObservationService.capped((float) a, (float) b, (float) result);
    return result;
  }
}
