package com.maidweapon.forge.compat.fox;

import com.maidweapon.forge.entity.BlackFoxMinorCutEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/** Miniature native OBJ/material, not a full-sized black hole or an extra texture pack. */
final class BlackFoxMinorCutRenderer extends EntityRenderer<BlackFoxMinorCutEntity> {
    BlackFoxMinorCutRenderer(EntityRendererProvider.Context context) { super(context); }
    @Override public ResourceLocation getTextureLocation(BlackFoxMinorCutEntity cut) {
        return ResourceLocation.parse("slashblade:model/util/slashdim.png");
    }
    @Override public void render(BlackFoxMinorCutEntity cut, float yaw, float partial, PoseStack stack,
                                 MultiBufferSource buffers, int light) {
        draw(cut.tickCount + partial, stack, buffers);
        super.render(cut, yaw, partial, stack, buffers, light);
    }
    void draw(float age, PoseStack stack, MultiBufferSource buffers) {
        stack.pushPose(); stack.scale(.55f, .55f, .55f);
        BlackFoxJudgementRenderer.draw(stack, buffers, age, false);
        BlackFoxJudgementRenderer.draw(stack, buffers, age, true);
        stack.popPose();
    }
}
