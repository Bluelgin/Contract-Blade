package com.maidweapon.forge.compat.fox;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.util.RenderUtils;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

/** Small bone-bound spirit glints. No particles, entities, random state or combat side effects. */
final class BlackFoxSpiritGlints {
    private record Anchor(String bone, float x, float y, float z) { }
    private static final Anchor[] ANCHORS = {
            new Anchor("LeftArm", .09f, -.09f, -.14f),
            new Anchor("RightArm", -.09f, -.09f, -.14f),
            new Anchor("DownBody", .25f, -.65f, -.14f),
            new Anchor("DownBody", -.25f, -.65f, -.14f),
            new Anchor("Tail5", 0, .06f, -.07f),
            new Anchor("LeftSpirit_Tail5", .08f, .06f, -.07f),
            new Anchor("RightSpirit_Tail5", -.08f, .06f, -.07f)
    };

    static void draw(AnimatedGeoModel model, PoseStack stack, MultiBufferSource buffers,
                     float age, int seed, boolean phaseTwo, int comboStage, float comboAge) {
        // Complete this quad pass before the native blade's triangle/glint buffers are requested.
        VertexConsumer vertices = buffers.getBuffer(RenderType.lightning());
        for (int i = 0; i < ANCHORS.length; i++) {
            float strength = pulse(age + Math.floorMod(seed, 137) + i * 23f, phaseTwo ? 86 : 142, 20);
            if (strength > 0) drawAt(model, stack, vertices, ANCHORS[i], .035f,
                    strength * (phaseTwo ? .32f : .22f));
        }
        if (comboStage > 0) {
            float strength = .18f * pulse(Math.max(0, comboAge), 10000, 12);
            if (strength > 0) drawAt(model, stack, vertices,
                    new Anchor("RightForeArm", 0, -.06f, -.14f), .07f, strength);
        }
        // A pair of faint points rising along the clothing, not a second model shell.
        float sweep = Math.floorMod(seed, 41) + age;
        float progress = (sweep % 180) / 34f;
        if (phaseTwo && progress >= 0 && progress < 1) {
            float opacity = (float) Math.sin(progress * Math.PI) * .14f;
            for (int side : new int[]{-1, 1}) drawAt(model, stack, vertices,
                    new Anchor("DownBody", side * .17f, -.6f + progress * .9f, -.20f), .045f, opacity);
        }
    }

    /** Smooth start/end, with silence for the rest of the cycle. Inputs are render ticks. */
    static float pulse(float age, float period, float duration) {
        float local = age % period;
        if (local < 0 || local >= duration) return 0;
        float sine = (float) Math.sin(local * Math.PI / duration);
        return sine * sine;
    }

    private static void drawAt(AnimatedGeoModel model, PoseStack stack, VertexConsumer vertices,
                               Anchor anchor, float radius, float opacity) {
        var path = BlackFoxBossRenderer.hierarchy(model.bones(), anchor.bone());
        if (path.isEmpty()) return;
        stack.pushPose();
        try {
            if (RenderUtils.prepMatrixForLocator(stack, path)) return;
            stack.translate(anchor.x(), anchor.y(), anchor.z());
            // Three narrow intersecting diamonds remain visible from any viewing angle.
            for (int plane = 0; plane < 3; plane++) {
                point(vertices, stack, plane, -radius * .35f, 0, opacity);
                point(vertices, stack, plane, 0, -radius, opacity);
                point(vertices, stack, plane, radius * .35f, 0, opacity);
                point(vertices, stack, plane, 0, radius, opacity);
            }
        } finally { stack.popPose(); }
    }

    private static void point(VertexConsumer vertices, PoseStack stack, int plane, float u, float v, float opacity) {
        float x = plane == 2 ? 0 : u;
        float y = plane == 1 ? 0 : v;
        float z = plane == 1 ? v : plane == 2 ? u : 0;
        vertices.vertex(stack.last().pose(), x, y, z).color(.67f, .27f, 1f, opacity).endVertex();
    }
    private BlackFoxSpiritGlints() { }
}
