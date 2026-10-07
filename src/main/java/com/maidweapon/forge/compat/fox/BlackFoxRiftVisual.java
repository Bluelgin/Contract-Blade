package com.maidweapon.forge.compat.fox;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

final class BlackFoxRiftVisual {
    static final ResourceLocation TEXTURE = ResourceLocation.parse("maid_weapon:textures/effect/black_fox_rift.png");
    static void draw(PoseStack stack, MultiBufferSource buffers, Vec3 offset, float size, float alpha, boolean floor,
                     org.joml.Quaternionf cameraOrientation, float age) {
        stack.pushPose();
        try {
            stack.translate(offset.x,offset.y+.03,offset.z);
            if (floor) stack.mulPose(Axis.XP.rotationDegrees(90));
            else stack.mulPose(cameraOrientation);
            BlackFoxEffectQuad.draw(stack,buffers,TEXTURE,size*.6f,size*1.6f,alpha*(BlackFoxEnergyShader.ready()?.55f:1));
            BlackFoxEffectQuad.energy(stack,buffers,false,size*.6f,size*1.6f,alpha,age);
        } finally { stack.popPose(); }
    }
    private BlackFoxRiftVisual() { }
}
