package com.maidweapon.forge.compat.tacz.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.maidweapon.forge.compat.tacz.TaczAmmoAccounting;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.*;

/** Account successful native script reductions; chamber transfers preserve the total. */
@Pseudo
@Mixin(targets = "com.tacz.guns.item.ModernKineticGunScriptAPI", remap = false)
public abstract class TaczSpentAmmoMixin {
    @Shadow private ItemStack itemStack;
    @WrapMethod(method = "reduceAmmoOnce")
    private boolean contractblade$spent(Operation<Boolean> original) {
        int before = TaczAmmoAccounting.loaded(itemStack);
        try { return original.call(); }
        finally { TaczAmmoAccounting.spent(itemStack, before); }
    }
}
