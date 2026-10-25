package com.maidweapon.forge.mixin;

import com.maidweapon.forge.compat.ContractProjectionIdentity;
import net.minecraft.world.inventory.*;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Reject before vanilla or TLM's quick-move implementation can copy/mutate a stack. */
@Mixin(value = AbstractContainerMenu.class, remap = false)
public abstract class ContractProjectionMenuMixin {
    @Inject(method = {"clicked", "m_150399_"}, at = @At("HEAD"), cancellable = true)
    private void contractblade$locked(int slot, int button, ClickType type, Player player, CallbackInfo ci) {
        AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
        if (ContractProjectionIdentity.isProjection(menu.getCarried())
                || slot >= 0 && slot < menu.slots.size()
                   && ContractProjectionIdentity.isProjection(menu.slots.get(slot).getItem())) {
            ci.cancel();
            if (!player.level().isClientSide) menu.broadcastFullState();
        }
    }
}
