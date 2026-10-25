package com.maidweapon.forge.mixin;

import com.maidweapon.forge.compat.ContractProjectionIdentity;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Slot.class, remap = false)
public abstract class ContractProjectionSlotMixin {
    @Inject(method = {"mayPickup", "m_8010_"}, at = @At("HEAD"), cancellable = true)
    private void contractblade$pickup(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (ContractProjectionIdentity.isProjection(((Slot)(Object)this).getItem())) cir.setReturnValue(false);
    }
    @Inject(method = {"mayPlace", "m_5857_"}, at = @At("HEAD"), cancellable = true)
    private void contractblade$place(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (ContractProjectionIdentity.isProjection(((Slot)(Object)this).getItem())
                || ContractProjectionIdentity.isProjection(stack)) cir.setReturnValue(false);
    }
}
