package com.maidweapon.port.mixin;
import com.maidweapon.port.projection.NativeProjection;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(value=Slot.class,remap=false)
public abstract class ProjectionSlotMixin {
 @Inject(method="mayPickup",at=@At("HEAD"),cancellable=true)
 private void contractblade$pickup(Player player,CallbackInfoReturnable<Boolean> result){if(NativeProjection.isCopy(((Slot)(Object)this).getItem()))result.setReturnValue(false);}
 @Inject(method="mayPlace",at=@At("HEAD"),cancellable=true)
 private void contractblade$place(ItemStack stack,CallbackInfoReturnable<Boolean> result){if(NativeProjection.isCopy(stack)||NativeProjection.isCopy(((Slot)(Object)this).getItem()))result.setReturnValue(false);}
}
