package com.maidweapon.forge.mixin;

import com.maidweapon.forge.system.akatsuki.soul.SoulDamageOverride;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(value = LivingHurtEvent.class, remap = false)
public abstract class SoulHurtAmountMixin {
  @Shadow private float amount;

  @Inject(method = "<init>", at = @At("RETURN"))
  private void contractblade$soulInitial(CallbackInfo ci) {
    amount = SoulDamageOverride.amount(this, amount, true);
  }

  @Inject(method = "setAmount", at = @At("HEAD"), cancellable = true)
  private void contractblade$soulSet(float value, CallbackInfo ci) {
    if (SoulDamageOverride.context(this) != null) {
      amount = SoulDamageOverride.amount(this, value, false);
      ci.cancel();
    }
  }

  @Inject(method = "getAmount", at = @At("HEAD"), cancellable = true)
  private void contractblade$soulGet(CallbackInfoReturnable<Float> ci) {
    if (SoulDamageOverride.context(this) != null)
      ci.setReturnValue(SoulDamageOverride.amount(this, amount, false));
  }
}
