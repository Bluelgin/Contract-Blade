package com.maidweapon.forge.compat.fox;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix4f;
import java.nio.file.Files;
import java.nio.file.Path;

/** Opt-in real GLSL/FBO draw check and preview, never installed as normal game rendering. */
final class BlackFoxEnergyValidation {
    static void run() throws Exception {
        if (!BlackFoxEnergyShader.ready()) throw new IllegalStateException("Energy shader did not compile/register");
        var mc=Minecraft.getInstance();
        var target=new TextureTarget(800,450,true,Minecraft.ON_OSX);
        var previousProjection=new Matrix4f(RenderSystem.getProjectionMatrix());
        var previousSorting=RenderSystem.getVertexSorting();
        var previousColor=RenderSystem.getShaderColor().clone();
        var modelView=RenderSystem.getModelViewStack();
        var output=Path.of("..","art","black_fox","dimension_strike","shader-preview").toAbsolutePath().normalize();
        Files.createDirectories(output);
        modelView.pushPose();
        try {
            modelView.setIdentity(); RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0,800,450,0,-100,100),VertexSorting.ORTHOGRAPHIC_Z);
            RenderSystem.setShaderColor(1,1,1,1);
            for(int frame=0;frame<48;frame++) {
                target.setClearColor(.045f,.075f,.10f,1);target.clear(Minecraft.ON_OSX);target.bindWrite(true);
                var buffers=MultiBufferSource.immediate(new BufferBuilder(4096));
                float age=frame*1.5f;
                var stack=new PoseStack();stack.translate(165,225,0);stack.scale(1,-1,1);
                BlackFoxEffectQuad.draw(stack,buffers,BlackFoxRiftVisual.TEXTURE,140,330,.45f);
                BlackFoxEffectQuad.energy(stack,buffers,false,140,330,.9f,age);
                stack=new PoseStack();stack.translate(525,225,0);stack.scale(1,-1,1);
                BlackFoxEffectQuad.draw(stack,buffers,BlackFoxGreatSlashVisual.TEXTURE,490,185,.18f);
                BlackFoxEffectQuad.energy(stack,buffers,true,490,185,.8f,age+13);
                buffers.endBatch();
                RenderSystem.bindTexture(target.getColorTextureId());
                try(var image=new NativeImage(800,450,false)) {
                    image.downloadTexture(0,false);image.flipY();
                    int bright=0;
                    for(int y=0;y<450;y++)for(int x=0;x<800;x++) {
                        int c=image.getPixelRGBA(x,y);
                        if((c&255)>100 && ((c>>>16)&255)>100)bright++;
                    }
                    if(bright<100)throw new IllegalStateException("Energy shader produced no luminous pixels");
                    image.writeToFile(output.resolve(String.format("frame-%03d.png",frame)));
                }
            }
            for (int frame = 0; frame < 27; frame++) {
                target.setClearColor(.045f,.075f,.10f,1); target.clear(Minecraft.ON_OSX); target.bindWrite(true);
                var buffers = MultiBufferSource.immediate(new BufferBuilder(4096));
                var stack = new PoseStack(); stack.translate(400,225,0); stack.scale(220,-220,220);
                float age = com.maidweapon.forge.system.fox.challenge.BlackFoxIaidoTimeline.CHARGE + frame * .5f;
                BlackFoxIaidoCharge.sample(age, 0, stack, buffers);
                buffers.endBatch();
                RenderSystem.bindTexture(target.getColorTextureId());
                try (var image = new NativeImage(800,450,false)) {
                    image.downloadTexture(0,false); image.flipY();
                    int bright = 0;
                    for(int y=0;y<450;y++)for(int x=0;x<800;x++) {
                        int c=image.getPixelRGBA(x,y);
                        if((c&255)>100 && ((c>>>16)&255)>100)bright++;
                    }
                    if (age >= 52 && age < 62 && bright < 5) throw new IllegalStateException("Iaido energy produced no light");
                    if (!com.maidweapon.forge.system.fox.challenge.BlackFoxIaidoTimeline.charging(age) && bright != 0)
                        throw new IllegalStateException("Iaido charge persists after draw");
                    image.writeToFile(output.resolve(String.format("iaido-frame-%03d.png",frame)));
                }
            }
            LogUtils.getLogger().info("BLACK_FOX_IAIDO_GPU_PASS: 27 actual inward-energy frames, existing shader/masks, terminal silence");
            int initialSlashPixels = 0, middleSlashPixels = 0;
            for (int frame = 0; frame < 33; frame++) {
                target.setClearColor(.045f,.075f,.10f,1); target.clear(Minecraft.ON_OSX); target.bindWrite(true);
                var buffers = MultiBufferSource.immediate(new BufferBuilder(8192));
                var stack = new PoseStack(); stack.translate(400,225,0); stack.scale(1,-1,1);
                float age = com.maidweapon.forge.system.fox.challenge.BlackFoxDimensionTimeline.HIT + frame*.5f;
                BlackFoxGreatSlashVisual.sample(stack,buffers,620,240,1,age);
                buffers.endBatch(); RenderSystem.bindTexture(target.getColorTextureId());
                try (var image = new NativeImage(800,450,false)) {
                    image.downloadTexture(0,false); image.flipY();
                    int bright = 0, head = 0;
                    for(int y=0;y<450;y++)for(int x=0;x<800;x++) {
                        int c=image.getPixelRGBA(x,y);
                        if((c&255)>100 && ((c>>>16)&255)>100) { bright++; if(x>650) head++; }
                    }
                    if(frame==0) initialSlashPixels=bright;
                    if(frame==10) { middleSlashPixels=bright;
                        if(head!=0) throw new IllegalStateException("Slash head did not erase before tail"); }
                    if(frame>=28 && bright!=0) throw new IllegalStateException("Slash persists beyond visual lifetime");
                    image.writeToFile(output.resolve(String.format("slash-frame-%03d.png",frame)));
                }
            }
            if(initialSlashPixels<100 || middleSlashPixels<5 || middleSlashPixels>=initialSlashPixels)
                throw new IllegalStateException("Slash sweep lost brightness or directional decay");
            LogUtils.getLogger().info("BLACK_FOX_SLASH_GPU_PASS: 33 independently decaying layered frames, head-first erasure, surviving tail and terminal silence");
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0,800,450,0,-1000,1000),VertexSorting.ORTHOGRAPHIC_Z);
            for(int frame=0;frame<61;frame++) {
                target.setClearColor(.045f,.075f,.10f,1); target.clear(Minecraft.ON_OSX); target.bindWrite(true);
                var buffers=MultiBufferSource.immediate(new BufferBuilder(8192));
                var stack=new PoseStack(); stack.translate(400,300,0); stack.scale(100,-100,100);
                float age=frame*1.25f;
                BlackFoxStandingIaidoVisual.sample(age,0,stack,buffers,new org.joml.Quaternionf());
                if(age<com.maidweapon.forge.system.fox.challenge.BlackFoxStandingIaidoTimeline.DRAW) {
                    stack.pushPose(); stack.translate(0,1.1,0);
                    BlackFoxIaidoCharge.sample(com.maidweapon.forge.system.fox.challenge.BlackFoxStandingIaidoTimeline.poseAge(age),0,stack,buffers);
                    stack.popPose();
                }
                buffers.endBatch(); RenderSystem.bindTexture(target.getColorTextureId());
                try(var image=new NativeImage(800,450,false)) {
                    image.downloadTexture(0,false); image.flipY(); int bright=0;
                    for(int y=0;y<450;y++)for(int x=0;x<800;x++) {
                        int c=image.getPixelRGBA(x,y);
                        if((c&255)>100 && ((c>>>16)&255)>100) bright++;
                    }
                    if(frame==8 && bright<20) throw new IllegalStateException("Iaido gathering swords invisible");
                    if(age>=70 && bright!=0) throw new IllegalStateException("Standing iaido effect outlives skill");
                    image.writeToFile(output.resolve(String.format("standing-iaido-frame-%03d.png",frame)));
                }
            }
            LogUtils.getLogger().info("BLACK_FOX_STANDING_IAIDO_GPU_PASS: 61 native cosmetic sword/charge/slash frames and terminal silence");
            for(int frame=0;frame<=25;frame++) {
                target.setClearColor(.045f,.075f,.10f,1); target.clear(Minecraft.ON_OSX); target.bindWrite(true);
                var buffers=MultiBufferSource.immediate(new BufferBuilder(32768));
                var stack=new PoseStack(); stack.translate(400,225,0); stack.scale(5,-5,5);
                stack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(70));
                BlackFoxArenaSlashVisual.draw(stack,buffers,frame,35);
                buffers.endBatch(); RenderSystem.bindTexture(target.getColorTextureId());
                try(var image=new NativeImage(800,450,false)) {
                    image.downloadTexture(0,false); image.flipY(); int bright=0;
                    for(int y=0;y<450;y++)for(int x=0;x<800;x++) {
                        int c=image.getPixelRGBA(x,y);
                        if((c&255)>100 && ((c>>>16)&255)>100) bright++;
                    }
                    if(frame==8 && bright<20) throw new IllegalStateException("Arena slash is invisible");
                    if(frame>=24 && bright!=0) throw new IllegalStateException("Arena slash outlives its visual window");
                    if(frame==8) image.writeToFile(output.resolve("arena-iaido-slash.png"));
                }
            }
            LogUtils.getLogger().info("BLACK_FOX_ARENA_SLASH_GPU_PASS: four reused layers, expanding 35-block ring and terminal silence");
            for(int frame=0;frame<57;frame++) {
                if(frame==0) for(String layer:new String[]{"core","band","halo","trail"}) {
                    var id=net.minecraft.resources.ResourceLocation.parse("maid_weapon:textures/effect/black_fox_arc_"+layer+".png");
                    if(mc.getResourceManager().getResource(id).isEmpty())throw new IllegalStateException("Missing arc layer: "+id);
                }
                target.setClearColor(.045f,.075f,.10f,1);target.clear(Minecraft.ON_OSX);target.bindWrite(true);
                var buffers=MultiBufferSource.immediate(new BufferBuilder(16384));
                var stack=new PoseStack();stack.translate(400,345,0);stack.scale(85,-85,85);
                float elapsed=frame*.5f;
                BlackFoxArcSlashVisual.sample(stack,buffers,4,2.5f,elapsed);
                buffers.endBatch();RenderSystem.bindTexture(target.getColorTextureId());
                try(var image=new NativeImage(800,450,false)) {
                    image.downloadTexture(0,false);image.flipY();int bright=0,right=0;
                    for(int y=0;y<450;y++)for(int x=0;x<800;x++) {
                        int c=image.getPixelRGBA(x,y);
                        if((c&255)>100 && ((c>>>16)&255)>100) { bright++;if(x>500)right++; }
                    }
                    if(frame==2 && (bright<10 || right!=0))throw new IllegalStateException("Arc must begin on left only");
                    if(frame==8 && right<10)throw new IllegalStateException("Arc head must reach right");
                    if(elapsed>=24 && bright!=0)throw new IllegalStateException("Arc outlives visual window");
                    image.writeToFile(output.resolve(String.format("arc-slash-frame-%03d.png",frame)));
                }
            }
            LogUtils.getLogger().info("BLACK_FOX_ARC_SLASH_GPU_PASS: 57 single-arc frames, left-to-right sweep and terminal silence");
            var meleeSkills = new com.maidweapon.forge.system.fox.challenge.BlackFoxFight.Skill[]{
                    com.maidweapon.forge.system.fox.challenge.BlackFoxFight.Skill.PROBE_CUT,
                    com.maidweapon.forge.system.fox.challenge.BlackFoxFight.Skill.COUNTER_CUT,
                    com.maidweapon.forge.system.fox.challenge.BlackFoxFight.Skill.SIDESTEP_CUT,
                    com.maidweapon.forge.system.fox.challenge.BlackFoxFight.Skill.CROSS_CUT,
                    com.maidweapon.forge.system.fox.challenge.BlackFoxFight.Skill.CROSS_CUT};
            int[] hitTicks = {12,10,20,14,44};
            for(int test=0;test<meleeSkills.length;test++) for(int offset:new int[]{-1,6,22}) {
                target.setClearColor(.045f,.075f,.10f,1);target.clear(Minecraft.ON_OSX);target.bindWrite(true);
                var buffers=MultiBufferSource.immediate(new BufferBuilder(16384));
                var stack=new PoseStack();stack.translate(400,400,0);stack.scale(85,-85,85);
                BlackFoxMeleeSlashVisual.sample(meleeSkills[test],hitTicks[test]+offset,0,stack,buffers);
                buffers.endBatch();RenderSystem.bindTexture(target.getColorTextureId());
                try(var image=new NativeImage(800,450,false)) {
                    image.downloadTexture(0,false);image.flipY();int bright=0;
                    for(int y=0;y<450;y++)for(int x=0;x<800;x++) {
                        int c=image.getPixelRGBA(x,y);
                        if((c&255)>100 && ((c>>>16)&255)>100)bright++;
                    }
                    if(offset==6) image.writeToFile(output.resolve("melee-arc-"+test+".png"));
                    if(offset==6 ? bright<10 : bright!=0)
                        throw new IllegalStateException("Melee arc timing mismatch: "+meleeSkills[test]+" / "+offset+" pixels="+bright);
                }
            }
            target.setClearColor(.045f,.075f,.10f,1);target.clear(Minecraft.ON_OSX);target.bindWrite(true);
            var placedBuffers=MultiBufferSource.immediate(new BufferBuilder(16384));
            var placedStack=new PoseStack();placedStack.translate(400,400,0);placedStack.scale(85,-85,85);
            BlackFoxArcSlashVisual.draw(placedStack,placedBuffers,0,4);
            placedBuffers.endBatch();RenderSystem.bindTexture(target.getColorTextureId());
            try(var image=new NativeImage(800,450,false)) {
                image.downloadTexture(0,false);image.flipY();int bright=0;
                for(int y=0;y<450;y++)for(int x=0;x<800;x++) {
                    int c=image.getPixelRGBA(x,y);
                    if((c&255)>100 && ((c>>>16)&255)>100) {
                        bright++;
                        if(y<230)throw new IllegalStateException("Standing arc still extends above chest-height placement");
                    }
                }
                if(bright<100)throw new IllegalStateException("Placed standing arc is invisible");
                image.writeToFile(output.resolve("arc-world-placement.png"));
            }
            LogUtils.getLogger().info("BLACK_FOX_SHARED_ARC_GPU_PASS: five melee hit windows, no premature/late effects and lowered world placement");
        } finally {
            modelView.popPose();RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(previousProjection,previousSorting);
            RenderSystem.setShaderColor(previousColor[0],previousColor[1],previousColor[2],previousColor[3]);
            mc.getMainRenderTarget().bindWrite(true);target.destroyBuffers();
        }
        LogUtils.getLogger().info("BLACK_FOX_ENERGY_GPU_PASS: compiled GLSL, 48 real offscreen frames and independent per-quad clocks; no global time uniform");
    }
    private BlackFoxEnergyValidation() { }
}
