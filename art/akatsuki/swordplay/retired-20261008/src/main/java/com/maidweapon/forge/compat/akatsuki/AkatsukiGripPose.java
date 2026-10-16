package com.maidweapon.forge.compat.akatsuki;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.*;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.util.RenderUtils;
import com.maidweapon.common.AkatsukiSwordTimeline;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.*;

/** Two-bone reach during extraction/insertion; no animation of legs, clothes, hair or tail. */
final class AkatsukiGripPose {
    static final Vector3f HIP = new Vector3f(-4.8f,-2,-3);
    private static final Quaternionf SHEATH_ROTATION = new Quaternionf().rotateX((float)java.lang.Math.toRadians(-70))
            .rotateZ((float)java.lang.Math.toRadians(-8));
    static void apply(AkatsukiSwordplay.Pose pose,boolean leftFree,float age) {
        var model=pose.model;
        if(pose.readyGrip==null)pose.readyGrip=readyGrip(model);
        pose.mouth.set(HIP);pose.extraction=false;pose.bladeInSheath=false;pose.bladeRotation.set(SHEATH_ROTATION);pose.sheathRotation.set(SHEATH_ROTATION);
        var motion=pose.timeline.motion();
        if(motion==AkatsukiSwordTimeline.Motion.IDLE)return;
        boolean draw=motion==AkatsukiSwordTimeline.Motion.DRAW, sheathe=motion==AkatsukiSwordTimeline.Motion.SHEATHE;
        if(draw || sheathe) {
            float progress=java.lang.Math.min(1,age/pose.timeline.duration());
            float p=draw?progress:1-progress;
            pose.extraction=true;pose.bladeInSheath=p<.28f;
            float pull=smooth((p-.28f)/.47f), settle=smooth((p-.75f)/.25f);
            // Scabbard moves backward while the right hand pulls forward, giving room for the full blade.
            pose.mouth.z+=leftFree?pull*6*(1-settle):0;
            var pulled=new Vector3f(3.8f,-1.6f,-12.0f);
            var target=new Vector3f(HIP).lerp(pulled,pull).lerp(pose.readyGrip,settle);
            if(p<.28f)target.set(pose.readyGrip).lerp(HIP,smooth(p/.28f));
            pose.grip.set(reach(model,"Right",target));
            if(!pose.bladeInSheath) {
                var direction=new Vector3f(pose.mouth).sub(pose.grip);
                if(direction.lengthSquared()>.001f)
                    pose.bladeRotation.rotationTo(new Vector3f(0,-1,0),direction.normalize());
                pose.sheathRotation.set(pose.bladeRotation).slerp(SHEATH_ROTATION,settle);
                // At the end of extraction, orientation returns to the same wrist-mounted ready pose.
                pose.bladeRotation.slerp(handRotationInBody(model),settle);
            }
            if(leftFree) {
                float leftWeight=draw?smooth(p/.2f):smooth(p/.2f);
                if(leftWeight>.001f)reach(model,"Left",new Vector3f(handInBody(model,"LeftHandLocator")).lerp(pose.mouth,leftWeight));
            }
        } else if(leftFree)reach(model,"Left",pose.mouth);
    }
    private static float smooth(float t) { t=java.lang.Math.max(0,java.lang.Math.min(1,t));return t*t*(3-2*t); }
    static Matrix4f locator(AnimatedGeoModel model,String name) {
        var stack=new PoseStack();RenderUtils.prepMatrixForLocator(stack,AkatsukiSwordVisual.hierarchy(model,name));
        return new Matrix4f(stack.last().pose());
    }
    private static Vector3f handInBody(AnimatedGeoModel model,String name) {
        var world=locator(model,name).getTranslation(new Vector3f());
        return locator(model,"UpperBody").invert().transformPosition(world).mul(16);
    }
    private static Quaternionf handRotationInBody(AnimatedGeoModel model) {
        return locator(model,"UpperBody").invert().mul(locator(model,"RightHandLocator"))
                .getNormalizedRotation(new Quaternionf());
    }
    private static Vector3f readyGrip(AnimatedGeoModel model) {
        var ready=AkatsukiSwordAssets.clip("ready");
        var saved=new java.util.ArrayList<float[]>();
        for(String name:java.util.List.of("RightArm","RightForeArm","RightHand")) {
            var bone=model.bones().get(name);saved.add(new float[]{bone.getRotationX(),bone.getRotationY(),bone.getRotationZ()});
            var v=ready.bones().get(name)[0];var initial=bone.getInitialSnapshot();float rad=(float)(java.lang.Math.PI/180);
            bone.setRotation(initial.rotationValueX-v.x()*rad,initial.rotationValueY-v.y()*rad,initial.rotationValueZ+v.z()*rad);
        }
        var target=handInBody(model,"RightHandLocator");int i=0;
        for(String name:java.util.List.of("RightArm","RightForeArm","RightHand")) {
            var v=saved.get(i++);model.bones().get(name).setRotation(v[0],v[1],v[2]);
        }
        return target;
    }
    /** Target and return value are relative to UpperBody's pivot, in model pixels. */
    private static Vector3f reach(AnimatedGeoModel model,String side,Vector3f target) {
        var upper=model.bones().get(side+"Arm");var fore=model.bones().get(side+"ForeArm");var hand=model.bones().get(side+"Hand");
        var parent=upper.geoBone().parent();
        var parentMatrix=locator(model,parent.name());var bodyMatrix=locator(model,"UpperBody");
        var targetWorld=bodyMatrix.transformPosition(new Vector3f(target).div(16));
        var goal=new Matrix4f(parentMatrix).invert().transformPosition(targetWorld);
        var shoulder=new Vector3f(upper.getPivotX()-parent.pivot().x,upper.getPivotY()-parent.pivot().y,
                upper.getPivotZ()-parent.pivot().z).div(16);
        var a=new Vector3f(fore.getPivotX()-upper.getPivotX(),fore.getPivotY()-upper.getPivotY(),fore.getPivotZ()-upper.getPivotZ()).div(16);
        var b=new Vector3f(hand.getPivotX()-fore.getPivotX(),hand.getPivotY()-fore.getPivotY(),hand.getPivotZ()-fore.getPivotZ()).div(16);
        float al=a.length(),bl=b.length();var direction=new Vector3f(goal).sub(shoulder);
        float distance=java.lang.Math.max(java.lang.Math.abs(al-bl)+.001f,java.lang.Math.min(al+bl-.001f,direction.length()));
        direction.normalize();goal.set(shoulder).add(new Vector3f(direction).mul(distance));
        var pole=side.equals("Left")?new Vector3f(-1,0,-.25f):new Vector3f(0,0,-1);
        pole.sub(new Vector3f(direction).mul(pole.dot(direction)));
        if(pole.lengthSquared()<.001f)pole.set(1,0,0).sub(new Vector3f(direction).mul(direction.x));
        pole.normalize();float along=(al*al+distance*distance-bl*bl)/(2*distance);
        float height=(float)java.lang.Math.sqrt(java.lang.Math.max(0,al*al-along*along));
        var elbow=new Vector3f(shoulder).add(new Vector3f(direction).mul(along)).add(pole.mul(height));
        var q=new Quaternionf().rotationTo(a,new Vector3f(elbow).sub(shoulder));
        var foreDirection=new Quaternionf(q).invert().transform(new Vector3f(goal).sub(elbow));
        var fq=new Quaternionf().rotationTo(b,foreDirection);var e=q.getEulerAnglesZYX(new Vector3f());
        upper.setRotation(e.x,e.y,e.z);e=fq.getEulerAnglesZYX(e);fore.setRotation(e.x,e.y,e.z);
        var wrist=parentMatrix.transformPosition(goal);
        return new Matrix4f(bodyMatrix).invert().transformPosition(wrist).mul(16);
    }
    private AkatsukiGripPose() { }
}
