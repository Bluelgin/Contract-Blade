package com.maidweapon.forge.compat.tacz.mixin;

import com.maidweapon.forge.compat.tacz.TaczPriorityAmmoHandler;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.tacz.guns.api.item.gun.AbstractGunItem", remap = false)
public abstract class TaczExtractAmmoMixin {
    @Inject(method = "findAndExtractInventoryAmmo", at = @At("HEAD"), cancellable = true)
    private void contractblade$actualDebit(IItemHandler inventory, ItemStack gun, int requested,
                                         CallbackInfoReturnable<Integer> result) {
        if (inventory instanceof TaczPriorityAmmoHandler priority) result.setReturnValue(priority.consume(gun, requested));
    }
}
