package com.maidweapon.port.mixin;
import com.maidweapon.port.projection.NativeProjection;
import net.minecraft.world.inventory.*;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Reject before native/TLM quick-move can copy or mutate equipment. */
@Mixin(value=AbstractContainerMenu.class,remap=false)
public abstract class ProjectionMenuMixin {
 @Inject(method="clicked",at=@At("HEAD"),cancellable=true)
 private void contractblade$locked(int index,int button,ClickType type,Player player,CallbackInfo ci){
  var menu=(AbstractContainerMenu)(Object)this;
  if(NativeProjection.isCopy(menu.getCarried())||index>=0&&index<menu.slots.size()&&NativeProjection.isCopy(menu.getSlot(index).getItem())){
   ci.cancel();if(!player.level().isClientSide)menu.broadcastFullState();
  }
 }
}
