package com.maidweapon.forge.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.maidweapon.forge.system.akatsuki.provenance.DamageObservationService;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = LivingEntity.class, remap = false)
public abstract class DamageObservationMixin {
  @WrapMethod(
      method = {"hurt", "m_6469_"},
      remap = false)
  private boolean cb$observe(DamageSource source, float amount, Operation<Boolean> original) {
    try (var scope =
        DamageObservationService.open(
            (LivingEntity) (Object) this,
            source,
            amount,
            "net.minecraft.world.entity.LivingEntity")) {
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

  @WrapMethod(
      method = {"getDamageAfterArmorAbsorb", "m_21161_"},
      remap = false)
  private float cb$armor(DamageSource source, float amount, Operation<Float> original) {
    float result = original.call(source, amount);
    DamageObservationService.defense((LivingEntity) (Object) this, source, amount, result, true);
    return result;
  }

  @WrapMethod(
      method = {"getDamageAfterMagicAbsorb", "m_6515_"},
      remap = false)
  private float cb$magic(DamageSource source, float amount, Operation<Float> original) {
    float result = original.call(source, amount);
    DamageObservationService.defense((LivingEntity) (Object) this, source, amount, result, false);
    return result;
  }
}
