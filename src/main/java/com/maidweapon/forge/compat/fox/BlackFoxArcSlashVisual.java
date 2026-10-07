package com.maidweapon.forge.compat.fox;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;

/** A single left-to-right curved cut, not intersecting texture planes. Pure skill-time sampling. */
final class BlackFoxArcSlashVisual {
    static void drawMelee(PoseStack stack,MultiBufferSource buffers,float yaw,float elapsed,float roll) {
        if(elapsed<0 || elapsed>=com.maidweapon.forge.system.fox.challenge.BlackFoxSlashPresentation.MELEE_END) return;
        stack.pushPose();
        try {
            stack.mulPose(Axis.YP.rotationDegrees(-yaw));
            for(int layer=0;layer<4;layer++) {
                var vertices=buffers.getBuffer(BlackFoxEnergyShader.arc(layer));
                float width=layer==0?.055f:layer==1?.32f:layer==2?.36f:.30f;
                float opacity=layer==0?1:layer==1?.85f:layer==2?.20f:.55f;
                for(int segment=0;segment<64;segment++) {
                    float t0=segment/64f,t1=(segment+1)/64f;
                    float a0=com.maidweapon.forge.system.fox.challenge.BlackFoxSlashPresentation.meleeAlpha(elapsed,t0,layer);
                    float a1=com.maidweapon.forge.system.fox.challenge.BlackFoxSlashPresentation.meleeAlpha(elapsed,t1,layer);
                    if(a0<=0 && a1<=0) continue;
                    for(int corner=0;corner<4;corner++) {
                        boolean right=corner==1 || corner==2;
                        float t=right?t1:t0,angle=com.maidweapon.forge.system.fox.challenge.BlackFoxSlashPresentation.meleeAngle(t);
                        float sin=(float)Math.sin(angle),cos=(float)Math.cos(angle);
                        float side=corner<2?-1:1;
                        // Entire solid cut stays within the exact server reach/cone; no forward offset or arbitrary scale.
                        float radius=(float)com.maidweapon.forge.system.fox.challenge.BlackFoxSlashPresentation.MELEE_REACH
                                -.40f+side*width;
                        float y=.9f+cos*.20f+sin*(float)Math.sin(Math.toRadians(roll))*.8f+side*.18f;
                        vertices.vertex(stack.last().pose(),sin*radius,y,cos*radius).color(1f,1f,1f,(right?a1:a0)*opacity)
                                .uv(t,corner<2?1:0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                                .normal(stack.last().normal(),0,1,0).endVertex();
                    }
                }
            }
            BlackFoxArcRiftVisual.sampleMelee(stack,buffers,elapsed,roll);
        } finally { stack.popPose(); }
    }
    static void draw(PoseStack stack,MultiBufferSource buffers,float yaw,float elapsed) {
        draw(stack,buffers,yaw,elapsed,1,0,1);
    }
    static void draw(PoseStack stack,MultiBufferSource buffers,float yaw,float elapsed,
                     float scale,float roll,float strength) {
        stack.pushPose();
        try {
            stack.mulPose(Axis.YP.rotationDegrees(-yaw));
            stack.translate(0,.9,2.3*scale);
            stack.mulPose(Axis.ZP.rotationDegrees(roll));
            stack.mulPose(Axis.XP.rotationDegrees(75));
            stack.scale(scale,scale,scale);
            sample(stack,buffers,4,2.5f,elapsed,strength);
        } finally { stack.popPose(); }
    }
    static void sample(PoseStack stack,MultiBufferSource buffers,float radius,float rise,float elapsed) {
        sample(stack,buffers,radius,rise,elapsed,1);
    }
    static void sample(PoseStack stack,MultiBufferSource buffers,float radius,float rise,float elapsed,float strength) {
        if(elapsed<0 || elapsed>=com.maidweapon.forge.system.fox.challenge.BlackFoxSlashPresentation.LIFETIME) return;
        // Four authored SVG textures share the same curved UV path, but have independent lifetimes.
        for(int layer=0;layer<4;layer++) {
            var vertices=buffers.getBuffer(BlackFoxEnergyShader.arc(layer));
            float width=layer==0?.06f:layer==1?.62f:layer==2?.68f:.65f;
            float life=layer==0?8:layer==1?16:layer==2?16:20;
            float opacity=(layer==0?1:layer==1?1:layer==2?.28f:.8f)*strength;
            for(int segment=0;segment<64;segment++) {
                float t0=segment/64f,t1=(segment+1)/64f;
                float a0=layerAlpha(elapsed,t0,life,layer),a1=layerAlpha(elapsed,t1,life,layer);
                if(a0<=0 && a1<=0) continue;
                for(int corner=0;corner<4;corner++) {
                    boolean right=corner==1 || corner==2;
                    float t=right?t1:t0,alpha=(right?a1:a0)*opacity;
                    float angle=(float)Math.PI*t;
                    float sin=(float)Math.sin(angle),cos=(float)Math.cos(angle);
                    // Keep a broad slash face; independently timed chunks tear away and residual pieces drift inward.
                    float taper=(float)Math.pow(Math.max(0,sin),.35);
                    float local=Math.max(0,elapsed-t*4);
                    float offset=layer==0?.49f:layer==3?-.07f-Math.max(0,local-4)*.018f:0;
                    float side=corner<2?-1:1;
                    float normal=(offset+side*width)*taper;
                    float x=-cos*radius+normal*-cos;
                    float y=sin*rise+normal*sin;
                    float z=sin*.3f;
                    vertices.vertex(stack.last().pose(),x,y,z).color(1f,1f,1f,alpha)
                            .uv(t,corner<2?1:0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                            .normal(stack.last().normal(),0,0,1).endVertex();
                }
            }
        }
        BlackFoxArcRiftVisual.sample(stack,buffers,radius,rise,elapsed,strength);
    }
    private static float layerAlpha(float elapsed,float along,float life,int layer) {
        float local=elapsed-along*4;
        if(local<0 || local>=life) return 0;
        if(layer==1) {
            // Broad opaque face first; then differently timed chunks tear away rather than turning into lines.
            int chunk=(int)(along*12);
            float breakAt=8+(chunk*7%11)*.22f;
            return Math.max(0,Math.min(1,1-(local-breakAt)/5));
        }
        if(layer==3) return Math.min(1,Math.max(0,(local-3)/1.5f))*alpha(elapsed,along,life)*1.5f;
        return alpha(elapsed,along,life);
    }
    static float alpha(float elapsed,float along,float life) {
        float local=elapsed-along*4;
        if(local<0 || local>=life) return 0;
        float fade=1-local/life;
        return fade*fade;
    }
    private BlackFoxArcSlashVisual() { }
}
