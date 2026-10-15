package com.maidweapon.forge.mixin;

import com.maidweapon.forge.system.akatsuki.soul.SoulDamageOverride;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.*;
import org.spongepowered.asm.mixin.Mixin;

/** Per-event overrides: Forge's base Event is loaded outside the transforming game classloader. */
@Mixin(
    value = {LivingAttackEvent.class, LivingHurtEvent.class, LivingDamageEvent.class},
    remap = false)
public abstract class SoulEventCancellationMixin extends LivingEvent {
  protected SoulEventCancellationMixin(LivingEntity entity) {
    super(entity);
  }

  @Override
  public void setCanceled(boolean canceled) {
    if (!SoulDamageOverride.rejectCancellation(this, canceled)) super.setCanceled(canceled);
  }

  @Override
  public boolean isCanceled() {
    return !SoulDamageOverride.uncancelable(this) && super.isCanceled();
  }
}
