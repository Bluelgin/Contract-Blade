package com.maidweapon.forge.compat.fox.mixin;

import com.maidweapon.forge.compat.BlackFoxGuardCompat;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observes native success only; does not alter timing, prerequisites or unrelated attacks. */
@Pseudo
@Mixin(targets = "mods.flammpfeil.slashblade.ability.Guard", remap = false)
public abstract class BlackFoxNativeGuardMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void contractBlade$installed(CallbackInfo ci) { BlackFoxGuardCompat.installed(); }

    @Inject(method = "onLivingAttack", at = @At(value = "INVOKE",
            target = "Lmods/flammpfeil/slashblade/ability/Untouchable;setUntouchable(Lnet/minecraft/world/entity/LivingEntity;I)V"))
    private void contractBlade$just(LivingAttackEvent event, CallbackInfo ci) { BlackFoxGuardCompat.just(event); }
}
