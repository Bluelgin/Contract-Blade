package com.maidweapon.forge.compat.fox;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import static com.maidweapon.forge.system.fox.challenge.BlackFoxSlashTimeline.Layer.*;
import static com.maidweapon.forge.system.fox.challenge.BlackFoxDimensionTimeline.HIT;

final class BlackFoxGreatSlashVisual {
    static final ResourceLocation TEXTURE = ResourceLocation.parse("maid_weapon:textures/effect/black_fox_great_slash.png");
    static void draw(PoseStack stack, MultiBufferSource buffers, float yaw, float scale, float alpha, float age) {
        stack.pushPose();
        try {
            stack.mulPose(Axis.YP.rotationDegrees(-yaw));
            stack.translate(0,1,4*scale);
            for (int angle : new int[]{70,110}) {
                stack.pushPose();
                stack.mulPose(Axis.YP.rotationDegrees(angle));
                sample(stack,buffers,8*scale,3*scale,alpha,age);
                stack.popPose();
            }
        } finally { stack.popPose(); }
    }
    static void sample(PoseStack stack, MultiBufferSource buffers, float width, float height, float alpha, float age) {
        // Same synchronized skill clock, distinct channel masks/lifetimes: no stacked solid triangles.
        BlackFoxEffectQuad.sweep(stack,buffers,TEXTURE,false,width,height,
                alpha*(BlackFoxEnergyShader.ready()?.07f:.75f),age,BAND);
        BlackFoxEffectQuad.sweep(stack,buffers,TEXTURE,true,width,height,alpha*.85f,age,BAND);
        BlackFoxEffectQuad.sweep(stack,buffers,TEXTURE,true,width,height,alpha,age,CORE);
        BlackFoxEffectQuad.sweep(stack,buffers,TEXTURE,true,width,height,alpha*.65f,age,HALO);
        stack.pushPose();
        // Only the weak residual layer drifts off the cut, never the committed attack direction.
        float drift = Math.max(0,age-HIT)/14;
        stack.translate(-width*.018f*drift,-height*.025f*drift,0);
        BlackFoxEffectQuad.sweep(stack,buffers,TEXTURE,true,width,height,alpha*.35f,age,TRAIL);
        stack.popPose();
    }
    private BlackFoxGreatSlashVisual() { }
}
