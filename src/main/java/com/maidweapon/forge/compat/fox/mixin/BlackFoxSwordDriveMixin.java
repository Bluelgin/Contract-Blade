package com.maidweapon.forge.compat.fox.mixin;

import com.maidweapon.forge.entity.BlackFoxSwordActorEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Drive overrides the sword collision method, so protect that override too. */
@Pseudo
@Mixin(targets = "mods.flammpfeil.slashblade.entity.EntityDrive", remap = false)
public abstract class BlackFoxSwordDriveMixin {
    @Inject(method = {"onHitEntity", "m_5790_"}, at = @At("HEAD"), cancellable = true, require = 1)
    private void contractblade$coreCollision(EntityHitResult hit, CallbackInfo callback) {
        Projectile drive = (Projectile)(Object)this;
        if (drive.getOwner() instanceof BlackFoxSwordActorEntity actor && !actor.permits(hit.getEntity())) callback.cancel();
    }
}
