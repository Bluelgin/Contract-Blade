package com.maidweapon.forge.compat.fox.mixin;

import com.maidweapon.forge.system.fox.ShrineOfferingService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Explicit Mojmap/SRG aliases support both userdev and the optional production mod. */
@Pseudo
@Mixin(targets = "mods.flammpfeil.slashblade.entity.BladeStandEntity", remap = false)
public abstract class ShrineBladeStandMixin {
    @Inject(method = {"hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z",
            "m_6469_(Lnet/minecraft/world/damagesource/DamageSource;F)Z"},
            at = @At("HEAD"), cancellable = true)
    private void maidWeapon$protectOffering(DamageSource source, float damage,
            CallbackInfoReturnable<Boolean> result) {
        if (ShrineOfferingService.isProtected((ItemFrame) (Object) this)) result.setReturnValue(false);
    }

    @Inject(method = {"interact(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;",
            "m_6096_(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;"},
            at = @At("HEAD"), cancellable = true)
    private void maidWeapon$requireOffering(Player player, InteractionHand hand,
            CallbackInfoReturnable<InteractionResult> result) {
        if (player instanceof ServerPlayer serverPlayer
                && !ShrineOfferingService.take((ItemFrame) (Object) this, serverPlayer, hand)) {
            result.setReturnValue(InteractionResult.SUCCESS);
        }
    }

    @Inject(method = {"interact(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;",
            "m_6096_(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;"},
            at = @At("RETURN"))
    private void maidWeapon$finishOffering(Player player, InteractionHand hand,
            CallbackInfoReturnable<InteractionResult> result) {
        if (player instanceof ServerPlayer serverPlayer)
            ShrineOfferingService.finishTake((ItemFrame) (Object) this, serverPlayer, hand);
    }
}
