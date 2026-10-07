package com.maidweapon.forge.compat.fox;

import com.maidweapon.forge.entity.BlackFoxCoreEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/** Reuses SlashBlade's OBJ renderer, without spawning projectile swarms. */
final class BlackFoxCoreRenderer extends EntityRenderer<BlackFoxCoreEntity> {
    BlackFoxCoreRenderer(EntityRendererProvider.Context context) { super(context); }
    @Override public ResourceLocation getTextureLocation(BlackFoxCoreEntity core) {
        return ResourceLocation.parse("slashblade:model/util/slashdim.png");
    }
    @Override public void render(BlackFoxCoreEntity core, float yaw, float partial, PoseStack stack,
                                 MultiBufferSource buffers, int light) {
        drawShell(core.tickCount + partial, stack, buffers);
        super.render(core, yaw, partial, stack, buffers, light);
    }
    void drawShell(float age, PoseStack stack, MultiBufferSource buffers) {
        stack.pushPose(); stack.translate(0, .75, 0);
        BlackFoxJudgementRenderer.draw(stack, buffers, age, false);
        float radius = 6 + 28 * net.minecraft.util.Mth.clamp(age / com.maidweapon.forge.system.fox.challenge.BlackFoxFight.CORE_FALL_TICKS, 0, 1);
        stack.scale(radius, .65f, radius);
        BlackFoxJudgementRenderer.draw(stack, buffers, age, true);
        stack.pushPose();
        float pulse = .82f + .04f * (float) Math.sin(age * .25);
        stack.scale(pulse, 1.6f, pulse);
        stack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-age * 7));
        BlackFoxJudgementRenderer.draw(stack, buffers, age + 18, true);
        stack.popPose();
        stack.popPose();
    }
}
