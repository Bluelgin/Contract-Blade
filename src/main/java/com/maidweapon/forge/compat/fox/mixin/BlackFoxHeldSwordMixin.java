package com.maidweapon.forge.compat.fox.mixin;

import com.maidweapon.forge.system.fox.challenge.BlackFoxCombatant;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Freeze only this encounter's held swords; do not let native collision consume their hit list. */
@Pseudo
@Mixin(targets = "mods.flammpfeil.slashblade.entity.EntityAbstractSummonedSword", remap = false)
public abstract class BlackFoxHeldSwordMixin {
    @Inject(method = "doForceHitEntity", at = @At("HEAD"), cancellable = true)
    private void contractblade$coreSafeHit(net.minecraft.world.entity.Entity target, CallbackInfo callback) {
        Projectile sword = (Projectile)(Object)this;
        if (sword.getOwner() instanceof com.maidweapon.forge.entity.BlackFoxSwordActorEntity actor && !actor.permits(target)) callback.cancel();
    }
    @Inject(method = {"onHitEntity", "m_5790_"}, at = @At("HEAD"), cancellable = true, require = 1)
    private void contractblade$coreSafeCollision(net.minecraft.world.phys.EntityHitResult hit, CallbackInfo callback) {
        Projectile sword = (Projectile)(Object)this;
        if (sword.getOwner() instanceof com.maidweapon.forge.entity.BlackFoxSwordActorEntity actor && !actor.permits(hit.getEntity())) callback.cancel();
    }
    @Shadow public abstract boolean isNoClip();
    @Inject(method = {"tick", "m_8119_"}, at = @At("HEAD"), cancellable = true, require = 1)
    private void contractblade$hold(CallbackInfo callback) {
        Projectile sword = (Projectile) (Object) this;
        if (!(sword.getOwner() instanceof BlackFoxCombatant boss)) return;
        boolean held = sword.level().isClientSide
                ? isNoClip() && sword.getDeltaMovement().lengthSqr() < .000001
                : boss.combat().ranged().held(sword);
        if (held) callback.cancel();
    }
}
