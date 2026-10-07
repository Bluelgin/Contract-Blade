package com.maidweapon.forge.compat.fox.mixin;

import com.maidweapon.forge.compat.BlackFoxNativeCombo;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;

/** Only Boss-owned native attacks bypass the player/PvP selector; their native bounds are retained. */
@Pseudo
@Mixin(targets = "mods.flammpfeil.slashblade.util.TargetSelector", remap = false)
public abstract class BlackFoxNativeTargetsMixin {
    @Inject(method = "getTargettableEntitiesWithinAABB(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/phys/AABB;D)Ljava/util/List;",
            at = @At("HEAD"), cancellable = true)
    private static void contractBlade$melee(Level level, LivingEntity attacker, AABB bounds, double reach,
            CallbackInfoReturnable<List<Entity>> ci) {
        var targets = BlackFoxNativeCombo.targets(attacker, bounds, reach);
        if (targets != null) ci.setReturnValue(new java.util.ArrayList<>(targets));
    }
    @Inject(method = "getTargettableEntitiesWithinAABB(Lnet/minecraft/world/level/Level;DLnet/minecraft/world/entity/Entity;)Ljava/util/List;",
            at = @At("HEAD"), cancellable = true)
    private static void contractBlade$projectile(Level level, double reach, Entity source,
            CallbackInfoReturnable<List<Entity>> ci) {
        var targets = BlackFoxNativeCombo.targets(source, source.getBoundingBox().inflate(reach), 0);
        if (targets != null) ci.setReturnValue(new java.util.ArrayList<>(targets));
    }
}
