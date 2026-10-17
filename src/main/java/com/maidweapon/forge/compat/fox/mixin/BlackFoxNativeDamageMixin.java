package com.maidweapon.forge.compat.fox.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.maidweapon.forge.system.fox.challenge.BlackFoxCombatant;
import com.maidweapon.forge.system.fox.challenge.BlackFoxDamageTuning;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.*;

/** Marks actual native Boss hits, not whichever skill happens to be active when an old shot lands. */
@Pseudo
@Mixin(targets = "mods.flammpfeil.slashblade.util.AttackHelper", remap = false)
public abstract class BlackFoxNativeDamageMixin {
    @WrapMethod(method = "attack(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/Entity;F)V", remap = false)
    private static void contractBlade$nativeDamage(LivingEntity attacker, Entity target, float ratio, Operation<Void> original) {
        if (!(attacker instanceof BlackFoxCombatant)) { original.call(attacker, target, ratio); return; }
        try (var ignored = BlackFoxDamageTuning.nativeHit(attacker, target)) { original.call(attacker, target, ratio); }
    }
}
