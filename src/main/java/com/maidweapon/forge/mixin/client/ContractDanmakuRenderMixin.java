package com.maidweapon.forge.mixin.client;

import com.maidweapon.forge.client.DanmakuProjectilePainter;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Client-only mixin. Every unrelated TLM/entity renderer remains untouched. */
@Mixin(EntityRenderDispatcher.class)
public abstract class ContractDanmakuRenderMixin {
    @Inject(method="render", at=@At("HEAD"), cancellable=true)
    private <E extends Entity> void manka$render(E entity, double x, double y, double z,
            float yaw, float partial, PoseStack pose, MultiBufferSource buffer, int light, CallbackInfo ci) {
        pose.pushPose(); pose.translate(x,y,z);
        boolean rendered=DanmakuProjectilePainter.render(entity,partial,pose,buffer);
        pose.popPose(); if(rendered) ci.cancel();
    }
}
