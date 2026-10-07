package com.maidweapon.forge.compat.fox;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;

/** Bounded expanding arena-wide slash; reuses the authored four-layer blade textures. Visual only. */
final class BlackFoxArenaSlashVisual {
    static void draw(PoseStack stack,MultiBufferSource buffers,float elapsed,float reach) {
        if(elapsed<0 || elapsed>=24) return;
        float radius=2+(reach-2)*(float)Math.pow(Math.min(1,elapsed/8),.7);
        for(int layer=0;layer<4;layer++) {
            var vertices=buffers.getBuffer(BlackFoxEnergyShader.arc(layer));
            float width=layer==0?.18f:layer==1?1.3f:layer==2?1.8f:1.6f;
            float alpha=(layer==2?.35f:layer==3?.55f:1)*(float)Math.pow(1-elapsed/24,1.3);
            for(int segment=0;segment<128;segment++) for(int corner=0;corner<4;corner++) {
                boolean right=corner==1 || corner==2;
                float t=(segment+(right?1:0))/128f,angle=t*(float)Math.PI*2;
                float side=corner<2?-1:1,r=radius+side*width;
                float u=((segment%32)+(right?1:0))/32f;
                vertices.vertex(stack.last().pose(),(float)Math.sin(angle)*r,1+side*.15f,(float)Math.cos(angle)*r)
                        .color(1f,1f,1f,alpha).uv(u,corner<2?1:0).overlayCoords(OverlayTexture.NO_OVERLAY)
                        .uv2(LightTexture.FULL_BRIGHT).normal(stack.last().normal(),0,1,0).endVertex();
            }
        }
    }
    private BlackFoxArenaSlashVisual() { }
}
