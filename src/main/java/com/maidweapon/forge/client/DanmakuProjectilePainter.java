package com.maidweapon.forge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;

/** Replaces only authenticated, UUID-matched native projectile visuals. Native collision stays intact. */
public final class DanmakuProjectilePainter {
    public static boolean render(Entity entity, float partial, PoseStack pose, MultiBufferSource buffer) {
        var frame=DanmakuWorldVisuals.projectileFrame(entity); if(frame==null) return false;
        int form=frame.event().form();
        String texture=form==0?"seal_needle":form==1?"seal_flower":form==2?"seal_guard":"petal";
        if(!DanmakuTextures.present(DanmakuTextures.path(texture))) return false;
        pose.pushPose(); pose.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        var movement=entity.getDeltaMovement();
        var camera=Minecraft.getInstance().gameRenderer.getMainCamera();
        var right=new org.joml.Vector3f(1,0,0).rotate(camera.rotation());
        var up=new org.joml.Vector3f(0,1,0).rotate(camera.rotation());
        double x=movement.x*right.x+movement.y*right.y+movement.z*right.z;
        double y=movement.x*up.x+movement.y*up.y+movement.z*up.z;
        pose.mulPose(Axis.ZP.rotation(form==0?(float)(Math.atan2(y,x)-Math.PI/2):(entity.tickCount+partial)*.06f));
        if(form==0) pose.scale(.32f,1,1);
        DanmakuWorldPainter.quad(pose,buffer,texture,form==0?.36f:form==1?.27f:.22f,1,-1);
        pose.popPose(); return true;
    }
    private DanmakuProjectilePainter() { }
}
