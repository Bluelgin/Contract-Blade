package com.maidweapon.forge.compat.fox.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.maidweapon.forge.system.akatsuki.provenance.NativeAttackOrigin;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import org.spongepowered.asm.mixin.*;

@Pseudo
@Mixin(targets = "mods.flammpfeil.slashblade.util.AttackManager", remap = false)
public abstract class SoulNativeMeleeMixin {
  @WrapMethod(
      method =
          "doMeleeAttack(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/Entity;ZZF)V",
      remap = false)
  private static void cb$melee(
      LivingEntity actor,
      Entity target,
      boolean force,
      boolean reset,
      float ratio,
      Operation<Void> original) {
    try (var origin = NativeAttackOrigin.open(actor, "SLASHBLADE_MELEE")) {
      original.call(actor, target, force, reset, ratio);
    }
  }

  @WrapMethod(
      method =
          "doAttackWith(Lnet/minecraft/world/damagesource/DamageSource;FLnet/minecraft/world/entity/Entity;ZZ)V",
      remap = false)
  private static void cb$strike(
      DamageSource source,
      float amount,
      Entity target,
      boolean force,
      boolean reset,
      Operation<Void> original) {
    try (var origin = NativeAttackOrigin.narrow(source, target)) {
      original.call(source, amount, target, force, reset);
    }
  }
}
