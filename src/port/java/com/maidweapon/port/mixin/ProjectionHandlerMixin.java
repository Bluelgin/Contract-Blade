package com.maidweapon.port.mixin;
import com.maidweapon.port.projection.NativeProjection;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(value=ItemStackHandler.class,remap=false)
public abstract class ProjectionHandlerMixin {
 @Shadow public abstract ItemStack getStackInSlot(int slot);
 @Inject(method="extractItem",at=@At("HEAD"),cancellable=true)
 private void contractblade$extract(int slot,int amount,boolean simulate,CallbackInfoReturnable<ItemStack> result){if(NativeProjection.isCopy(getStackInSlot(slot)))result.setReturnValue(ItemStack.EMPTY);}
}
