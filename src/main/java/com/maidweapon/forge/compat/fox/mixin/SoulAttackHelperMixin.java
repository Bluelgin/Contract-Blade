package com.maidweapon.forge.compat.fox.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.maidweapon.forge.system.akatsuki.provenance.NativeAttackOrigin;
import net.minecraft.world.entity.*;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
/** 1.9.65's AttackHelper calls Entity.hurt directly, without Player.attack. */
@Pseudo
@Mixin(targets="mods.flammpfeil.slashblade.util.AttackHelper",remap=false)
public abstract class SoulAttackHelperMixin {
 @WrapMethod(method="attack(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/Entity;F)V",remap=false)
 private static void cb$calculation(LivingEntity actor,Entity target,float ratio,Operation<Void> original){try(var origin=NativeAttackOrigin.open(actor,"SLASHBLADE_ATTACK_HELPER")){original.call(actor,target,ratio);}}
 @WrapOperation(method="attack",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"),require=0)
 private static boolean cb$hurt(Entity target,DamageSource source,float amount,Operation<Boolean> original){try(var origin=NativeAttackOrigin.narrow(source,target,amount)){return original.call(target,source,amount);}}
 @WrapOperation(method="attack",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/Entity;m_6469_(Lnet/minecraft/world/damagesource/DamageSource;F)Z"),require=0)
 private static boolean cb$hurtSrg(Entity target,DamageSource source,float amount,Operation<Boolean> original){try(var origin=NativeAttackOrigin.narrow(source,target,amount)){return original.call(target,source,amount);}}
}
