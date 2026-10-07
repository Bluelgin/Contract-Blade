package com.maidweapon.forge.compat.akatsuki;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.*;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.util.RenderUtils;
import com.maidweapon.common.AkatsukiSwordTimeline;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.Mob;
import org.joml.*;
import java.util.*;

/** One small solid mesh pass. Both parts share the same material; no item renderer/glint recursion. */
public final class AkatsukiSwordVisual {
    private static final int[][] CORNERS={{0,2,3,1},{5,7,6,4},{4,6,2,0},{1,3,7,5},{2,6,7,3},{4,0,1,5}};
    private static final float[][] NORMALS={{0,0,-1},{0,0,1},{-1,0,0},{1,0,0},{0,1,0},{0,-1,0}};
    public static void render(Mob entity,float partial,PoseStack stack,MultiBufferSource buffers,int light) {
        if(!AkatsukiSwordplay.replaces(entity))return;
        var pose=AkatsukiSwordplay.pose(entity); var model=pose.model;
        var vertices=buffers.getBuffer(RenderType.entityCutoutNoCull(AkatsukiSwordAssets.MATERIAL));
        stack.pushPose();
        try {
            // Stable body-mounted mouth; it never follows an idle dangling arm or the tail.
            if(RenderUtils.prepMatrixForLocator(stack,hierarchy(model,"UpperBody")))return;
            stack.translate(pose.mouth.x/16,pose.mouth.y/16,pose.mouth.z/16);
            stack.mulPose(pose.extraction?pose.sheathRotation:new Quaternionf().rotateX((float)java.lang.Math.toRadians(-70)).rotateZ((float)java.lang.Math.toRadians(-8)));
            draw("sheath",stack,vertices,light);
            if(pose.timeline.motion()==AkatsukiSwordTimeline.Motion.IDLE || pose.extraction && pose.bladeInSheath)draw("blade",stack,vertices,light);
        } finally {stack.popPose();}
        if(pose.timeline.motion()==AkatsukiSwordTimeline.Motion.IDLE || pose.extraction && pose.bladeInSheath)return;
        stack.pushPose();
        try {
            if(pose.extraction) {
                if(RenderUtils.prepMatrixForLocator(stack,hierarchy(model,"UpperBody")))return;
                stack.translate(pose.grip.x/16,pose.grip.y/16,pose.grip.z/16);
                stack.mulPose(pose.bladeRotation);
            } else if(RenderUtils.prepMatrixForLocator(stack,hierarchy(model,"RightHandLocator")))return;
            draw("blade",stack,vertices,light);
        } finally {stack.popPose();}
    }
    static List<AnimatedGeoBone> hierarchy(AnimatedGeoModel model,String name) {
        var path=new ArrayList<AnimatedGeoBone>();
        for(var bone=model.bones().get(name);bone!=null;
            bone=bone.geoBone().parent()==null?null:model.bones().get(bone.geoBone().parent().name()))path.add(bone);
        Collections.reverse(path);return path;
    }
    private static void draw(String part,PoseStack stack,VertexConsumer vertices,int light) {
        var pose=stack.last();
        for(var b:AkatsukiSwordAssets.part(part)) {
            for(int face=0;face<6;face++) {
                if((b.faceMask()&(1<<face))==0)continue;
                var n=NORMALS[face];
                for(int corner:CORNERS[face])vertices.vertex(pose.pose(),(corner&1)==0?b.x0():b.x1(),
                                (corner&2)==0?b.y0():b.y1(),(corner&4)==0?b.z0():b.z1())
                        .color((b.rgb()>>16)&255,(b.rgb()>>8)&255,b.rgb()&255,255).uv(.5f,.5f)
                        .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(pose.normal(),n[0],n[1],n[2]).endVertex();
            }
        }
    }
    private AkatsukiSwordVisual() { }
}
