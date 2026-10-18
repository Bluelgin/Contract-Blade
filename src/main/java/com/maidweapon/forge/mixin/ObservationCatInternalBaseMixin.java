package com.maidweapon.forge.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.maidweapon.forge.system.akatsuki.provenance.DamageObservationService;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;

/** Cataclysm 3.31 InternalAnimationMonster branch, including Netherite Monstrosity.
 * The plugin gates exact version plus native Math.min(FF) cap bytecode.
 */
@Pseudo
@Mixin(
    targets =
        "com.github.L_Ender.cataclysm.entity.InternalAnimationMonster.IABossMonsters.IABoss_monster",
    remap = false)
public abstract class ObservationCatInternalBaseMixin {
  @WrapMethod(method = {"hurt", "m_6469_"}, remap = false)
  private boolean cb$observe(DamageSource source, float amount, Operation<Boolean> original) {
    try (var scope = DamageObservationService.open(
        (LivingEntity) (Object) this, source, amount,
        "com.github.L_Ender.cataclysm.entity.InternalAnimationMonster.IABossMonsters.IABoss_monster")) {
      try {
        boolean value = original.call(source, amount);
        scope.returned(value);
        return value;
      } catch (RuntimeException | Error failure) {
        scope.failed();
        throw failure;
      }
    }
  }

  @WrapOperation(
      method = {"hurt", "m_6469_"},
      at = @At(value = "INVOKE", target = "Ljava/lang/Math;min(FF)F"),
      require = 1)
  private float cb$cap(float a, float b, Operation<Float> original) {
    float result = original.call(a, b);
    DamageObservationService.capped(a, b, result);
    return result;
  }
}
