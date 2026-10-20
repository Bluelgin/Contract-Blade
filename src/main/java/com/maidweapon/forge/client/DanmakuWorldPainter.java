package com.maidweapon.forge.client;

import com.maidweapon.forge.api.danmaku.DanmakuVisualEvent;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Layered world glyphs are anchored to server events, never screen-space attacks. */
@Mod.EventBusSubscriber(modid="maid_weapon", value=Dist.CLIENT)
public final class DanmakuWorldPainter {
    private static int quads;
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        var mc = Minecraft.getInstance(); if (mc.level == null) return;
        quads = 0; var pose = event.getPoseStack();
        var buffer = MultiBufferSource.immediate(new BufferBuilder(8192));
        var camera = event.getCamera().getPosition();
        pose.pushPose(); pose.translate(-camera.x, -camera.y, -camera.z);
        float partial = event.getPartialTick(); long now = mc.level.getGameTime();
        for (var f : DanmakuWorldVisuals.GUARDS.values()) {
            var owner = mc.level.getPlayerByUUID(f.event().owner());
            if (owner == null) continue;
            float age = now - f.received() + partial;
            float alpha = Math.min(1, age / 5) * Math.min(1, (f.life() - age) / 8f) * .55f;
            Vec3 center = owner.getPosition(partial).add(0, .65, 0);
            glyph(pose, buffer, center, "seal_guard", 2.3f, age * .025f, alpha, 78, owner.getYRot(), -1);
            glyph(pose, buffer, center.add(0,.80,0), "seal_guard", 1.45f, -age*.02f, alpha*.5f, 18, owner.getYRot(), -1);
        }
        for (var f : DanmakuWorldVisuals.FRAMES) {
            var e = f.event(); float age = now - f.received() + partial;
            float alpha = Math.max(0, 1 - age / f.life());
            if (e.kind() == DanmakuVisualEvent.Kind.INTERCEPT) {
                glyph(pose, buffer, e.position(), "intercept_flash", .7f + age*.035f, age*.1f, alpha, 0, e.yaw(), -1);
            } else if (e.kind() == DanmakuVisualEvent.Kind.HIT) {
                if (e.form() != 1 && e.form() != 3) continue;
                for (int i=0;i<5;i++) {
                    double a = i*Math.PI*2/5;
                    glyph(pose,buffer,e.position().add(Math.cos(a)*age*.045,age*.025,Math.sin(a)*age*.045),
                            "petal", .18f, (float)a, alpha*.65f, 0, e.yaw(), -1);
                }
            } else if (e.kind() == DanmakuVisualEvent.Kind.GUARD_STOP) {
                glyph(pose,buffer,e.position().add(0,.65,0),"seal_guard",2.3f+age*.035f,age*.02f,alpha*.4f,78,e.yaw(),-1);
            } else if (e.kind() == DanmakuVisualEvent.Kind.WAVE) {
                if (e.form()==0) {
                    Vec3 right = new Vec3(Math.cos(Math.toRadians(e.yaw())),0,Math.sin(Math.toRadians(e.yaw())));
                    Vec3 back = new Vec3(Math.sin(Math.toRadians(e.yaw())),0,-Math.cos(Math.toRadians(e.yaw()))).scale(.45);
                    for (int side : new int[]{-1,1}) {
                        Vec3 pos=e.position().add(back).add(right.scale(side*.9));
                        glyph(pose,buffer,pos,"icon_needle",.45f,side*.25f,alpha*.7f,0,e.yaw(),-1);
                        glyph(pose,buffer,pos,"seal_needle",.8f,side*.25f,alpha*.55f,0,e.yaw(),-1);
                    }
                } else if(e.form()==1) {
                    Vec3 front = new Vec3(-Math.sin(Math.toRadians(e.yaw())),0,Math.cos(Math.toRadians(e.yaw())));
                    float r = e.phase()==0 ? .5f+age*.035f : Math.max(.4f,1.4f-age*.03f);
                    glyph(pose,buffer,e.position().add(front.scale(2.4)),"seal_flower",r,age*.025f*(e.phase()==0?1:-1),alpha*.6f,25,e.yaw(),-1);
                } else if(e.form()==3) {
                    float stage=e.phase()+Math.min(1,age/14f);
                    // Every atlas region uses independent UVs. Fourth wave always keeps all four layers.
                    for(int layer=0;layer<=e.phase();layer++) {
                        float radius=(3.8f-layer*.58f)*(1-Math.max(0,stage-2)*.12f);
                        glyph(pose,buffer,e.position(),"fantasy_layers",radius,
                                age*.017f*(layer%2==0?1:-1)+layer*.38f,
                                alpha*.65f,62+layer*5,e.yaw(),layer);
                    }
                    if(e.phase()==3) glyph(pose,buffer,e.position(),"final_seal",1.6f-age*.02f,
                            -age*.015f,alpha*.65f,55,e.yaw(),-1);
                }
            }
        }
        buffer.endBatch(); pose.popPose();
    }
    private static void glyph(PoseStack pose, MultiBufferSource buffer, Vec3 position, String name,
                              float radius, float angle, float alpha, float tilt, float yaw, int tile) {
        int cap = Minecraft.getInstance().options.particles().get() == net.minecraft.client.ParticleStatus.ALL ? 512 : 144;
        if (quads++ >= cap || radius<=0 || alpha<=0 || !DanmakuTextures.present(DanmakuTextures.path(name))) return;
        pose.pushPose(); pose.translate(position.x,position.y,position.z);
        pose.mulPose(Axis.YP.rotationDegrees(-yaw)); pose.mulPose(Axis.XP.rotationDegrees(tilt));
        pose.mulPose(Axis.ZP.rotation(angle));
        quad(pose,buffer,name,radius,alpha,tile); pose.popPose();
    }
    static void quad(PoseStack pose, MultiBufferSource buffer, String name, float radius, float alpha, int tile) {
        var out=buffer.getBuffer(DanmakuRenderTypes.texture(DanmakuTextures.path(name)));
        var matrix=pose.last().pose();
        float u=tile<0?0:(tile%2)*.5f, v=tile<0?0:(tile/2)*.5f, span=tile<0?1:.5f;
        out.vertex(matrix,-radius,-radius,0).uv(u,v+span).color(1f,1f,1f,alpha).endVertex();
        out.vertex(matrix,radius,-radius,0).uv(u+span,v+span).color(1f,1f,1f,alpha).endVertex();
        out.vertex(matrix,radius,radius,0).uv(u+span,v).color(1f,1f,1f,alpha).endVertex();
        out.vertex(matrix,-radius,radius,0).uv(u,v).color(1f,1f,1f,alpha).endVertex();
    }
    private DanmakuWorldPainter() { }
}
