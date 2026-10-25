package com.maidweapon.forge.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.maidweapon.forge.system.akatsuki.provenance.DamageObservationService;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.ForgeHooks;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = ForgeHooks.class, remap = false)
public abstract class DamageObservationForgeMixin {
  @WrapMethod(
      method =
          "onLivingAttack(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/damagesource/DamageSource;F)Z",
      remap = false)
  private static boolean cb$attack(
      LivingEntity t, DamageSource s, float n, Operation<Boolean> original) {
    boolean r = original.call(t, s, n);
    DamageObservationService.attack(t, s, n, r);
    return r;
  }

  @WrapMethod(
      method =
          "onLivingHurt(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/damagesource/DamageSource;F)F",
      remap = false)
  private static float cb$hurt(LivingEntity t, DamageSource s, float n, Operation<Float> original) {
    float r = original.call(t, s, n);
    DamageObservationService.hurt(t, s, n, r);
    return r;
  }

  @WrapMethod(
      method =
          "onLivingDamage(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/damagesource/DamageSource;F)F",
      remap = false)
  private static float cb$damage(
      LivingEntity t, DamageSource s, float n, Operation<Float> original) {
    float r = original.call(t, s, n);
    DamageObservationService.damage(t, s, n, r);
    return r;
  }
}
