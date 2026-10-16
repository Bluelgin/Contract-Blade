package com.maidweapon.forge.compat.akatsuki.mixin;

import com.github.tartaricacid.touhoulittlemaid.client.entity.GeckoMaidEntity;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.event.predicate.AnimationEvent;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.molang.context.AnimationContext;
import com.maidweapon.forge.compat.akatsuki.AkatsukiSwordplay;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets="com.github.tartaricacid.touhoulittlemaid.client.entity.GeckoMaidEntity",remap=false)
public abstract class AkatsukiGeckoPoseMixin {
    @Inject(method="setCustomAnimations",at=@At("HEAD"),require=1)
    private void contractblade$restore(AnimationContext<?> context, AnimationEvent<?> event, CallbackInfoReturnable<Boolean> ci) {
        AkatsukiSwordplay.restore((GeckoMaidEntity<?>)(Object)this);
    }
    @Inject(method="setCustomAnimations",at=@At("RETURN"),require=1)
    private void contractblade$pose(AnimationContext<?> context, AnimationEvent<?> event, CallbackInfoReturnable<Boolean> ci) {
        AkatsukiSwordplay.apply((GeckoMaidEntity<?>)(Object)this,event.getPartialTick());
    }
}
