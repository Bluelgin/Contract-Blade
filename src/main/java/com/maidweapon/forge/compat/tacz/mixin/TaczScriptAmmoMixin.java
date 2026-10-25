package com.maidweapon.forge.compat.tacz.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.maidweapon.forge.compat.tacz.TaczLoadedCompat;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.core.Direction;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "com.tacz.guns.item.ModernKineticGunScriptAPI", remap = false)
public abstract class TaczScriptAmmoMixin {
    @WrapOperation(method = {"consumeAmmoFromPlayer", "hasAmmoToConsume"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;getCapability(Lnet/minecraftforge/common/capabilities/Capability;Lnet/minecraft/core/Direction;)Lnet/minecraftforge/common/util/LazyOptional;"))
    private LazyOptional<IItemHandler> contractblade$ammoView(LivingEntity shooter,
            Capability<IItemHandler> capability, Direction side,
            Operation<LazyOptional<IItemHandler>> original) {
        return TaczLoadedCompat.ammoView(shooter, original.call(shooter, capability, side));
    }
}
