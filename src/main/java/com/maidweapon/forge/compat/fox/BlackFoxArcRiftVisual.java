package com.maidweapon.forge.compat.fox;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;

/** Existing rift artwork bent into the slash's inner edge; local clocks, no effect entities. */
final class BlackFoxArcRiftVisual {
    static void sample(PoseStack stack, MultiBufferSource buffers, float radius, float rise, float elapsed, float strength) {
        if (elapsed < 0 || elapsed >= com.maidweapon.forge.system.fox.challenge.BlackFoxSlashPresentation.LIFETIME) return;
        draw(stack, buffers, radius, rise, elapsed, strength, false,false,0);
        if (BlackFoxEnergyShader.ready()) draw(stack, buffers, radius, rise, elapsed, strength, true,false,0);
    }
    static void sampleMelee(PoseStack stack,MultiBufferSource buffers,float elapsed,float roll) {
        draw(stack,buffers,0,0,elapsed,.7f,false,true,roll);
        if(BlackFoxEnergyShader.ready()) draw(stack,buffers,0,0,elapsed,.7f,true,true,roll);
    }

    private static void draw(PoseStack stack, MultiBufferSource buffers, float radius, float rise,
                             float elapsed, float strength, boolean energy,boolean melee,float roll) {
        var vertices = buffers.getBuffer(energy ? BlackFoxEnergyShader.type(false)
                : RenderType.entityTranslucentEmissive(BlackFoxRiftVisual.TEXTURE));
        // Three connected fractures reuse the full rift mask, with its long axis following the arc.
        for (int fracture = 0; fracture < 3; fracture++) {
            for (int segment = 0; segment < 24; segment++) {
                float s0 = segment / 24f, s1 = (segment + 1) / 24f;
                float t0 = (fracture + s0) / 3, t1 = (fracture + s1) / 3;
                if (opacity(elapsed,t0,melee)<=0 && opacity(elapsed,t1,melee)<=0) continue;
                for (int corner = 0; corner < 4; corner++) {
                    float s = corner == 1 || corner == 2 ? s1 : s0;
                    float t = (fracture + s) / 3, local = Math.max(0, elapsed - (melee
                            ? com.maidweapon.forge.system.fox.challenge.BlackFoxSlashPresentation.arrival(t) : t*4));
                    float angle = (float) Math.PI * t;
                    float sin = (float) Math.sin(angle), cos = (float) Math.cos(angle);
                    float taper = (float) Math.pow(Math.max(0, sin), .35);
                    float opening = .12f + .88f * Math.min(1, local / .8f);
                    // Close inward as the broad face tears away, rather than overlaying another full slash.
                    float closing = Math.max(0, Math.min(1, (local - 10) / 7));
                    float side = corner < 2 ? -1 : 1;
                    float normal = (-.40f - closing * .20f + side * .28f * opening * (1-closing)) * taper;
                    float u = corner < 2 ? 1 : 0;
                    float v = 1 - s;
                    float opacity = opacity(elapsed,t,melee) * strength * (energy ? .42f : BlackFoxEnergyShader.ready() ? .65f : 1);
                    // Energy atlas has transparent margins; sample the same authored shape as the base mask.
                    if (energy) { u = (48 + u * 128) / 224; v = (48 + v * 320) / 416; }
                    float x=-cos*radius-normal*cos,y=sin*rise+normal*sin,z=sin*.3f;
                    if(melee) {
                        float theta=com.maidweapon.forge.system.fox.challenge.BlackFoxSlashPresentation.meleeAngle(t);
                        float r=(float)com.maidweapon.forge.system.fox.challenge.BlackFoxSlashPresentation.MELEE_REACH-.85f+side*.18f;
                        x=(float)Math.sin(theta)*r; z=(float)Math.cos(theta)*r;
                        y=.9f+(float)Math.cos(theta)*.20f+(float)Math.sin(theta)*(float)Math.sin(Math.toRadians(roll))*.8f+side*.08f;
                    }
                    var vertex = vertices.vertex(stack.last().pose(),x,y,z).color(1f,1f,1f,opacity).uv(u,v);
                    if (energy) vertex.uv2(Math.round(local * 100),0).endVertex();
                    else vertex.overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                            .normal(stack.last().normal(),0,0,1).endVertex();
                }
            }
        }
    }
    private static float opacity(float elapsed,float along,boolean melee) {
        return melee ? com.maidweapon.forge.system.fox.challenge.BlackFoxSlashPresentation.meleeAlpha(elapsed,along,2)
                : visibility(elapsed,along);
    }

    static float visibility(float elapsed, float along) {
        float local = elapsed - along * 4;
        if (local < 0 || local >= 18) return 0;
        return Math.min(1, local / .6f) * Math.max(0, Math.min(1, (18-local) / 8));
    }

    private BlackFoxArcRiftVisual() { }
}
