package com.maidweapon.forge.compat.fox;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import com.maidweapon.forge.system.fox.challenge.BlackFoxSlashTimeline;

/** Texture-backed fullbright quad. Callers own positioning and time envelopes. */
final class BlackFoxEffectQuad {
    static void draw(PoseStack stack, MultiBufferSource buffers, ResourceLocation texture, float width, float height, float alpha) {
        if (alpha <= 0) return;
        var vertices = buffers.getBuffer(RenderType.entityTranslucentEmissive(texture));
        float[] xs = {-width/2,width/2,width/2,-width/2}, ys = {-height/2,-height/2,height/2,height/2};
        for (int i=0;i<4;i++) vertices.vertex(stack.last().pose(), xs[i],ys[i],0).color(1f,1f,1f,alpha)
                .uv(i==0||i==3?0:1,i<2?1:0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                .normal(stack.last().normal(),0,0,1).endVertex();
    }
    private BlackFoxEffectQuad() { }
    /** Shared geometric mask for base texture and glow, including the no-custom-shader fallback. */
    static void sweep(PoseStack stack, MultiBufferSource buffers, ResourceLocation texture,
                      boolean energy, float width, float height, float alpha, float age, BlackFoxSlashTimeline.Layer layer) {
        alpha *= BlackFoxSlashTimeline.brightness(age, layer);
        if (alpha <= 0 || (energy && !BlackFoxEnergyShader.ready())) return;
        float margin = energy ? 48f / 608 : 0;
        float span = 1 - margin * 2;
        float actualWidth = energy ? width * 608f / 512 : width;
        float actualHeight = energy ? height * 288f / 192 : height;
        var vertices = buffers.getBuffer(energy ? BlackFoxEnergyShader.type(true)
                : RenderType.entityTranslucentEmissive(texture));
        int clock = Math.round(Math.max(0, Math.min(100, age)) * 100);
        // One batch, 32 strips; no entities, per-frame arrays or global shader uniforms.
        for (int slice = 0; slice < 32; slice++) {
            float u0 = slice / 32f, u1 = (slice + 1) / 32f;
            // Existing artwork's pointed head is on +X: erase that end first, leave its trailing end last.
            float a0 = BlackFoxSlashTimeline.visibility(age, 1-(u0-margin)/span, layer);
            float a1 = BlackFoxSlashTimeline.visibility(age, 1-(u1-margin)/span, layer);
            if (a0 <= 0 && a1 <= 0) continue;
            for (int corner = 0; corner < 4; corner++) {
                boolean right = corner == 1 || corner == 2;
                float u = right ? u1 : u0, fade = right ? a1 : a0;
                float v = corner < 2 ? 1 : 0;
                // Keep UV geometry fixed: squeezing fading columns would kink the curved cut into a zigzag.
                float y = (v == 1 ? -1 : 1) * actualHeight / 2;
                var vertex = vertices.vertex(stack.last().pose(), (u-.5f)*actualWidth,y,0)
                        .color(1f,1f,1f,alpha*fade).uv(u,v);
                if (energy) vertex.uv2(clock,layer.shaderKind).endVertex();
                else vertex.overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                        .normal(stack.last().normal(),0,0,1).endVertex();
            }
        }
    }
    static void energy(PoseStack stack, MultiBufferSource buffers, boolean slash, float width, float height,
                       float alpha, float age) {
        if (alpha <= 0 || !BlackFoxEnergyShader.ready()) return;
        // Energy masks have a 48px transparent margin on each side; keep the original shape's world size.
        width *= slash ? 608f/512 : 224f/128;
        height *= slash ? 288f/192 : 416f/320;
        var vertices = buffers.getBuffer(BlackFoxEnergyShader.type(slash));
        int clock = Math.round(Math.max(0,Math.min(100,age))*100);
        float[] xs={-width/2,width/2,width/2,-width/2}, ys={-height/2,-height/2,height/2,height/2};
        for(int i=0;i<4;i++) vertices.vertex(stack.last().pose(),xs[i],ys[i],0).color(1f,1f,1f,alpha)
                .uv(i==0||i==3?0:1,i<2?1:0).uv2(clock,slash?1:0).endVertex();
    }
}
