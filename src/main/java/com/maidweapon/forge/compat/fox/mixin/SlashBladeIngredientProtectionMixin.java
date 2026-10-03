package com.maidweapon.forge.compat.fox.mixin;

import com.maidweapon.forge.system.fox.FoxSpiritState;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** SlashBlade overrides Ingredient.test instead of delegating to the vanilla predicate. */
@Pseudo
@Mixin(targets = "mods.flammpfeil.slashblade.recipe.SlashBladeIngredient", remap = false)
public abstract class SlashBladeIngredientProtectionMixin {
    @Inject(method = "test(Lnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)
    private void maidWeapon$protectResident(ItemStack stack, CallbackInfoReturnable<Boolean> result) {
        if (FoxSpiritState.isProtected(stack)) result.setReturnValue(false);
    }
}
