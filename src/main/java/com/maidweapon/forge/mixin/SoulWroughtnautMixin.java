package com.maidweapon.forge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.maidweapon.common.SoulDamageConfig;
import com.maidweapon.forge.system.akatsuki.soul.SoulStrikeContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;

/** Mowzie 1.8.2 only: choose its native environmental bypass branch for an exact soul strike. */
@Pseudo
@Mixin(targets = "com.bobmowzie.mowziesmobs.server.entity.wroughtnaut.EntityWroughtnaut", remap = false)
public abstract class SoulWroughtnautMixin {
  @WrapOperation(
      method = {"hurt", "m_6469_"},
      at = {
        @At(value = "INVOKE", target = "Lnet/minecraft/world/damagesource/DamageSource;getEntity()Lnet/minecraft/world/entity/Entity;", remap = false),
        @At(value = "INVOKE", target = "Lnet/minecraft/world/damagesource/DamageSource;m_7639_()Lnet/minecraft/world/entity/Entity;", remap = false)
      },
      remap = false, require = 1, allow = 1)
  private Entity soul$weaknessAttacker(DamageSource source, Operation<Entity> original) {
    Entity attacker = original.call(source);
    var self = (LivingEntity) (Object) this;
    var scope = SoulStrikeContext.current();
    if (scope == null || !scope.strong || !scope.matches(self, source)
        || !(attacker instanceof ServerPlayer player) || !scope.attacker.equals(player.getUUID()))
      return attacker;
    // Preserve the normal hit's wake-up/aggro behavior. The actual DamageSource is
    // untouched, so parent hurt(), kill credit, events and loot still see the player.
    if (!player.isCreative() && self instanceof Mob mob && mob.getTarget() == null)
      mob.setTarget(player);
    if (SoulDamageConfig.DIAGNOSTICS.get())
      com.mojang.logging.LogUtils.getLogger().info(
          "SOUL_WROUGHTNAUT_NATIVE_BYPASS owner={} target={} sequence={}",
          player.getUUID(), self.getUUID(), scope.sequence);
    return null;
  }
}
