package com.maidweapon.forge.mixin;

import com.maidweapon.forge.system.fox.FoxSpiritState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Predicate#test keeps its name in 1.20.1 SRG; no fragile result-slot repair. */
@Mixin(value = Ingredient.class, remap = false)
public abstract class FoxIngredientProtectionMixin {
    @Inject(method = "test(Lnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)
    private void maidWeapon$protectResident(ItemStack stack, CallbackInfoReturnable<Boolean> result) {
        if (FoxSpiritState.isProtected(stack)) result.setReturnValue(false);
    }
}
