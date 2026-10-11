package com.maidweapon.forge.compat.fox;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

final class BlackFoxRiftVisual {
    static final ResourceLocation TEXTURE = ResourceLocation.parse("maid_weapon:textures/effect/black_fox_rift.png");
    private static final ResourceLocation TEAR = ResourceLocation.parse("maid_weapon:textures/effect/black_fox_space_tear.png");
    /** World-oriented slit, not a camera-facing portal. One atlas and no persistent render state. */
    static void tear(PoseStack stack, MultiBufferSource buffers, Vec3 offset, float yaw,
                     boolean floor, float frame, float alpha) {
        if (alpha <= 0 || frame < 0 || frame >= 23) return;
        stack.pushPose();
        try {
            stack.translate(offset.x, offset.y + .035, offset.z);
            stack.mulPose(Axis.YP.rotationDegrees(-yaw));
            if (floor) stack.mulPose(Axis.XP.rotationDegrees(90));
            else stack.translate(0, 0, -.12); // Behind the emerging body, not covering its face.
            float open = Math.min(1, Math.max(0, (frame - 5) / 6));
            float width = floor ? 1.25f : 1.45f + .35f * open;
            float height = floor ? 2.3f : 3.1f;
            int first = (int) frame;
            float blend = frame - first;
            BlackFoxEffectQuad.atlas(stack, buffers, TEAR, width, height, alpha * (1 - blend), first);
            if (blend > .001f) BlackFoxEffectQuad.atlas(stack, buffers, TEAR, width, height, alpha * blend, first + 1);
        } finally { stack.popPose(); }
    }
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
