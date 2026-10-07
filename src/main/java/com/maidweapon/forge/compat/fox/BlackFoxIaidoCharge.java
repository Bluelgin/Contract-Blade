package com.maidweapon.forge.compat.fox;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.util.RenderUtils;
import com.maidweapon.forge.entity.BlackFoxBossEntity;
import com.maidweapon.forge.system.fox.challenge.BlackFoxFight;
import com.maidweapon.forge.system.fox.challenge.BlackFoxIaidoTimeline;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Bone-bound inward wisps. Reuses energy masks; no particles, timers or persistent render state. */
final class BlackFoxIaidoCharge {
    static void draw(AnimatedGeoModel model, BlackFoxBossEntity boss, float partial,
                     PoseStack stack, MultiBufferSource buffers) {
        if (!com.maidweapon.forge.system.fox.challenge.BlackFoxStandingIaidoTimeline.pose(boss.skill())) return;
        float age = boss.skillAge(partial);
        if(boss.skill()==BlackFoxFight.Skill.STANDING_IAIDO)
            age=com.maidweapon.forge.system.fox.challenge.BlackFoxStandingIaidoTimeline.poseAge(age);
        if (!BlackFoxIaidoTimeline.charging(age)) return;
        var anchor = BlackFoxBossRenderer.hierarchy(model.bones(), "LeftHandLocator");
        if (anchor.isEmpty()) return;
        stack.pushPose();
        try {
            if (RenderUtils.prepMatrixForLocator(stack, anchor)) return;
            sample(age, boss.getId(), stack, buffers);
        } finally { stack.popPose(); }
    }

    static void sample(float age, int seed, PoseStack stack, MultiBufferSource buffers) {
        float outer = BlackFoxIaidoTimeline.outerAlpha(age);
        float core = BlackFoxIaidoTimeline.coreAlpha(age);
        if (outer <= 0 && core <= 0) return;
        float elapsed = age - BlackFoxIaidoTimeline.CHARGE;
        float progress = BlackFoxIaidoTimeline.progress(age);
        for (int ray = 0; ray < 6 && outer > 0; ray++) {
            double angle = ray * Math.PI / 3 + Math.floorMod(seed, 29) * .09;
            var direction = new Vector3f((float)Math.cos(angle), (ray % 2 == 0 ? .35f : -.25f),
                    (float)Math.sin(angle)).normalize();
            for (int pulse = 0; pulse < 2; pulse++) {
                float cycle = (elapsed * .12f + ray * .173f + pulse * .5f) % 1;
                float radius = BlackFoxIaidoTimeline.radius(cycle, .75f - progress * .18f);
                float alpha = outer * (float)Math.sin(cycle * Math.PI) * .75f;
                stack.pushPose();
                try {
                    stack.translate(direction.x * radius, direction.y * radius, direction.z * radius);
                    stack.mulPose(new Quaternionf().rotationTo(new Vector3f(0, 1, 0),
                            new Vector3f(direction).negate()));
                    ribbon(stack, buffers, .035f, .13f + .05f * cycle, alpha, age + ray * 2, 1);
                    stack.mulPose(Axis.YP.rotationDegrees(90));
                    ribbon(stack, buffers, .035f, .13f + .05f * cycle, alpha * .6f, age + ray * 2, 1);
                } finally { stack.popPose(); }
            }
        }
        // Small concentrated mouth light, never a body-sized sphere or duplicated body shell.
        float size = .065f + progress * .035f;
        for (int plane = 0; plane < 3; plane++) {
            stack.pushPose();
            try {
                if (plane == 1) stack.mulPose(Axis.YP.rotationDegrees(90));
                if (plane == 2) stack.mulPose(Axis.XP.rotationDegrees(90));
                ribbon(stack, buffers, size, size * 1.5f, core, age, 3);
            } finally { stack.popPose(); }
        }
    }
    private static void ribbon(PoseStack stack, MultiBufferSource buffers, float width, float height,
                               float alpha, float age, int energyLayers) {
        BlackFoxEffectQuad.draw(stack, buffers, BlackFoxRiftVisual.TEXTURE, width, height,
                alpha * (BlackFoxEnergyShader.ready() ? .16f : 1));
        // A few additive core layers brighten the reused mask without changing the global shader.
        for (int layer = 0; layer < energyLayers; layer++)
            BlackFoxEffectQuad.energy(stack, buffers, false, width, height, alpha, age);
    }
    private BlackFoxIaidoCharge() { }
}
