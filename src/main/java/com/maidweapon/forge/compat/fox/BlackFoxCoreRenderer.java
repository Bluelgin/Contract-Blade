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
        if (core.isSeal()) {
            stack.pushPose(); stack.translate(0, .75, 0);
            var owner = core.level().getEntity(core.ownerId());
            if (owner != null && owner.isAlive()) {
                double x = net.minecraft.util.Mth.lerp(partial, owner.xOld, owner.getX()) - net.minecraft.util.Mth.lerp(partial, core.xOld, core.getX());
                double y = net.minecraft.util.Mth.lerp(partial, owner.yOld, owner.getY()) + owner.getBbHeight() * .55 - net.minecraft.util.Mth.lerp(partial, core.yOld, core.getY()) - .75;
                double z = net.minecraft.util.Mth.lerp(partial, owner.zOld, owner.getZ()) - net.minecraft.util.Mth.lerp(partial, core.zOld, core.getZ());
                // Vanilla adds a two-block crystal pedestal offset internally; cancel it for this core.
                stack.pushPose(); stack.translate(0, -2, 0);
                net.minecraft.client.renderer.entity.EnderDragonRenderer.renderCrystalBeams((float)x, (float)y, (float)z, partial, core.tickCount, stack, buffers, light);
                stack.popPose();
            }
            BlackFoxJudgementRenderer.draw(stack, buffers, core.tickCount + partial, false);
            stack.scale(2.5f, .7f, 2.5f);
            BlackFoxJudgementRenderer.draw(stack, buffers, core.tickCount + partial, true);
            stack.popPose();
        } else drawShell(core.tickCount + partial, stack, buffers);
        super.render(core, yaw, partial, stack, buffers, light);
    }
    @Override public boolean shouldRender(BlackFoxCoreEntity core, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z) {
        if (super.shouldRender(core, frustum, x, y, z)) return true;
        var owner = core.isSeal() ? core.level().getEntity(core.ownerId()) : null;
        return owner != null && frustum.isVisible(core.getBoundingBox().minmax(owner.getBoundingBox()).inflate(1));
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
