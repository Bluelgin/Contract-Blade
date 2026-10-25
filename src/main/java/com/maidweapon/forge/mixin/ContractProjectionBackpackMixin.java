package com.maidweapon.forge.mixin;

import com.maidweapon.forge.compat.ContractProjectionIdentity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ItemStackHandler.class, remap = false)
public abstract class ContractProjectionBackpackMixin {
    @Shadow public abstract ItemStack getStackInSlot(int slot);
    @Inject(method = "extractItem", at = @At("HEAD"), cancellable = true)
    private void contractblade$extract(int slot, int amount, boolean simulate, CallbackInfoReturnable<ItemStack> cir) {
        if (ContractProjectionIdentity.isProjection(getStackInSlot(slot))) cir.setReturnValue(ItemStack.EMPTY);
    }
}
