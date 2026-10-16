package com.maidweapon.forge.compat.fox.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.maidweapon.forge.system.fox.challenge.BlackFoxAttackTrace;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.*;

/** Whole-call scope also closes when an optional projectile throws or recursively ticks another. */
@Pseudo
@Mixin(
    targets = {
      "mods.flammpfeil.slashblade.entity.EntitySlashEffect",
      "mods.flammpfeil.slashblade.entity.EntityJudgementCut"
    },
    remap = false)
public abstract class SlashEffectProvenanceMixin {
  @WrapMethod(
      method = {"tick()V", "m_8119_()V"},
      remap = false)
  private void cb$tick(Operation<Void> original) {
    try (var scope = BlackFoxAttackTrace.open((Entity) (Object) this)) {
      original.call();
    }
  }
}
