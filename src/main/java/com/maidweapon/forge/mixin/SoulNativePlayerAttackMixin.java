package com.maidweapon.forge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.maidweapon.forge.system.akatsuki.provenance.NativeAttackOrigin;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Dev/SRG aliases are separate operations; an absent alias grants no capability. */
@Mixin(value = Player.class, remap = false)
public abstract class SoulNativePlayerAttackMixin {
  @WrapOperation(
      method = {"attack", "m_5706_"},
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"),
      require = 0)
  private boolean cb$attack(
      Entity target, DamageSource source, float amount, Operation<Boolean> original) {
    try (var origin =
        NativeAttackOrigin.strike((Entity) (Object) this, "PLAYER_ATTACK", source, target)) {
      return original.call(target, source, amount);
    }
  }

  @WrapOperation(
      method = {"attack", "m_5706_"},
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/entity/Entity;m_6469_(Lnet/minecraft/world/damagesource/DamageSource;F)Z"),
      require = 0)
  private boolean cb$attackSrg(
      Entity target, DamageSource source, float amount, Operation<Boolean> original) {
    try (var origin =
        NativeAttackOrigin.strike((Entity) (Object) this, "PLAYER_ATTACK", source, target)) {
      return original.call(target, source, amount);
    }
  }

  @WrapOperation(
      method = {"attack", "m_5706_"},
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/entity/LivingEntity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"),
      require = 0)
  private boolean cb$sweep(
      net.minecraft.world.entity.LivingEntity target,
      DamageSource source,
      float amount,
      Operation<Boolean> original) {
    try (var origin =
        NativeAttackOrigin.strike((Entity) (Object) this, "PLAYER_SWEEP", source, target)) {
      return original.call(target, source, amount);
    }
  }

  @WrapOperation(
      method = {"attack", "m_5706_"},
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/entity/LivingEntity;m_6469_(Lnet/minecraft/world/damagesource/DamageSource;F)Z"),
      require = 0)
  private boolean cb$sweepSrg(
      net.minecraft.world.entity.LivingEntity target,
      DamageSource source,
      float amount,
      Operation<Boolean> original) {
    try (var origin =
        NativeAttackOrigin.strike((Entity) (Object) this, "PLAYER_SWEEP", source, target)) {
      return original.call(target, source, amount);
    }
  }
}
