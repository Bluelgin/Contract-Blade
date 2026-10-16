package com.maidweapon.forge.compat.fox;

import com.maidweapon.forge.entity.BlackFoxSwordCoreEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;

/** One persistent visual plus four orbiting space apertures; no per-frame entities or textures. */
final class BlackFoxSwordCoreRenderer extends EntityRenderer<BlackFoxSwordCoreEntity> {
    BlackFoxSwordCoreRenderer(EntityRendererProvider.Context context) { super(context); }
    @Override public ResourceLocation getTextureLocation(BlackFoxSwordCoreEntity entity) { return ResourceLocation.parse("slashblade:model/util/slashdim.png"); }
    @Override public void render(BlackFoxSwordCoreEntity entity, float yaw, float partial, PoseStack stack, MultiBufferSource buffers, int light) {
        float age = entity.tickCount + partial;
        stack.pushPose();
        float scale = Math.min(1, age / 12) * Math.max(.05f, Math.min(1, (com.maidweapon.forge.system.fox.BlackFoxSwordCoreTimeline.DURATION - age) / 8));
        stack.scale(scale, scale, scale);
        BlackFoxJudgementRenderer.draw(stack, buffers, age, false);
        stack.pushPose(); stack.scale(10, .9f, 10); BlackFoxJudgementRenderer.draw(stack, buffers, age, true); stack.popPose();
        for (int aperture = 0; aperture < 4; aperture++) {
            double angle = age * (entity.following() ? .06 : .025) + aperture * Math.PI / 2;
            stack.pushPose(); stack.translate(Math.cos(angle) * 2.4, 0, Math.sin(angle) * 2.4); stack.scale(.75f, .75f, .75f);
            BlackFoxJudgementRenderer.draw(stack, buffers, age + aperture * 6, false); stack.popPose();
        }
        stack.popPose(); super.render(entity, yaw, partial, stack, buffers, light);
    }
}
