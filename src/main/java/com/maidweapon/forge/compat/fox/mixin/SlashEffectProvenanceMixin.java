package com.maidweapon.forge.compat.fox.mixin;

import com.maidweapon.forge.system.fox.challenge.BlackFoxAttackTrace;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Preserve native slash provenance while areaAttack() rewrites the damage source to its shooter. */
@Pseudo
@Mixin(targets = {"mods.flammpfeil.slashblade.entity.EntitySlashEffect", "mods.flammpfeil.slashblade.entity.EntityJudgementCut"}, remap = false)
public abstract class SlashEffectProvenanceMixin {
    @Inject(method = {"tick()V", "m_8119_()V"}, at = @At("HEAD"))
    private void contractBlade$beginHit(CallbackInfo ci) { BlackFoxAttackTrace.enter((Entity) (Object) this); }
    @Inject(method = {"tick()V", "m_8119_()V"}, at = @At("RETURN"))
    private void contractBlade$endHit(CallbackInfo ci) { BlackFoxAttackTrace.exit(); }
}
