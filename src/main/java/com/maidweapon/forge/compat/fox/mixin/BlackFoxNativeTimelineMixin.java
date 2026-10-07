package com.maidweapon.forge.compat.fox.mixin;

import com.maidweapon.forge.compat.BlackFoxNativeCombo;
import com.maidweapon.forge.system.fox.challenge.BlackFoxCombatant;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.Map;
import java.util.function.Consumer;

/** Registered timelines share a mutable offset. Boss clocks must not shift another player's timeline. */
@Pseudo
@Mixin(targets = "mods.flammpfeil.slashblade.registry.combo.ComboState$TimeLineTickAction", remap = false)
public abstract class BlackFoxNativeTimelineMixin {
    @Shadow Map<Integer, Consumer<LivingEntity>> timeLine;
    @Inject(method = "accept(Lnet/minecraft/world/entity/LivingEntity;)V", at = @At("HEAD"), cancellable = true)
    private void contractBlade$sessionClock(LivingEntity entity, CallbackInfo ci) {
        if (!(entity instanceof BlackFoxCombatant)) return;
        var action = timeLine.get((int) BlackFoxNativeCombo.elapsed(entity));
        if (action != null) action.accept(entity);
        ci.cancel();
    }
}
