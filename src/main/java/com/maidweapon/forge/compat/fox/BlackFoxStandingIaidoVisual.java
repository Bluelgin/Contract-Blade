package com.maidweapon.forge.compat.fox;

import com.maidweapon.forge.entity.BlackFoxBossEntity;
import com.maidweapon.forge.system.fox.challenge.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.lang.reflect.Method;

/** Repeating waves of 24 visual native swords; analytic inward paths, no projectile entities. */
final class BlackFoxStandingIaidoVisual {
    private record Bridge(Object manager,Method model,Method color,Method luminous) { }
    private static Bridge bridge;
    private static Bridge api() throws ReflectiveOperationException {
        if(bridge!=null) return bridge;
        var manager=Class.forName("mods.flammpfeil.slashblade.client.renderer.model.BladeModelManager");
        var obj=Class.forName("mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject");
        var state=Class.forName("mods.flammpfeil.slashblade.client.renderer.util.BladeRenderState");
        Class<?>[] args={ItemStack.class,obj,String.class,ResourceLocation.class,PoseStack.class,MultiBufferSource.class,int.class};
        return bridge=new Bridge(manager.getMethod("getInstance").invoke(null),manager.getMethod("getModel",ResourceLocation.class),
                state.getMethod("setCol",int.class),state.getMethod("renderOverridedLuminous",args));
    }
    static void draw(BlackFoxBossEntity boss,float partial,PoseStack stack,MultiBufferSource buffers) {
        if(boss.skill()!=BlackFoxFight.Skill.STANDING_IAIDO) return;
        float age=boss.skillAge(partial);
        if(age>=BlackFoxStandingIaidoTimeline.HIT) return; // The fixed server cut snapshot owns the slash.
        sample(age,boss.getYRot(),stack,buffers,
                net.minecraft.client.Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
    }
    static void sample(float age,float yaw,PoseStack stack,MultiBufferSource buffers,Quaternionf camera) {
        if(age<0 || age>=BlackFoxStandingIaidoTimeline.END) return;
        // Repeating tilted-ring streams accelerate inward; stop emission early enough to absorb the final wave.
        if(age<BlackFoxStandingIaidoTimeline.LOCK) for(int slot=0;slot<24;slot++) {
            float t=BlackFoxStandingIaidoTimeline.streamProgress(age,slot);
            if(t>=1) continue;
            double angle=slot*Math.PI/12+age*.035;
            Vec3 direction=new Vec3(Math.cos(angle),Math.sin(angle)*.45,Math.sin(angle)).normalize();
            float radius=3.2f*(1-t*t);
            float alpha=Math.min(1,age/6)*(1-t);
            Vec3 offset=direction.scale(radius).add(0,1.1,0);
            if(t<.18f) BlackFoxRiftVisual.draw(stack,buffers,offset,.3f,alpha*.6f,false,camera,age+slot);
            stack.pushPose();
            try {
                stack.translate(offset.x,offset.y,offset.z);
                stack.mulPose(new Quaternionf().rotationTo(new Vector3f(0,0,1),
                        new Vector3f((float)-direction.x,(float)-direction.y,(float)-direction.z)));
                // Once close to the body the sword shrinks into a light streak rather than visibly impaling it.
                float length=.65f*(1-t*.85f);
                stack.pushPose(); stack.mulPose(Axis.XP.rotationDegrees(90));
                BlackFoxEffectQuad.energy(stack,buffers,false,.045f,length,alpha*.6f,age); stack.popPose();
                // Native ss.obj's pointed axis is +Z (unlike the held blade OBJ).
                stack.scale(.002f*(1-t),.002f*(1-t),.002f*(1-t));
                nativeSword(stack,buffers,alpha);
            } finally { stack.popPose(); }
        }
        if(age>=BlackFoxStandingIaidoTimeline.HIT) {
            BlackFoxArcSlashVisual.draw(stack,buffers,yaw,age-BlackFoxStandingIaidoTimeline.HIT);
        }
    }
    private static void nativeSword(PoseStack stack,MultiBufferSource buffers,float alpha) {
        try {
            var api=api();
            Object model=api.model().invoke(api.manager(),ResourceLocation.parse("slashblade:model/util/ss.obj"));
            api.color().invoke(null,(Math.round(alpha*255)<<24)|0xC584FF);
            api.luminous().invoke(null,ItemStack.EMPTY,model,"ss",ResourceLocation.parse("slashblade:model/util/ss.png"),stack,buffers,15728880);
            api.color().invoke(null,0xFFFFFFFF);
        } catch(ReflectiveOperationException error) { throw new IllegalStateException("Iaido cosmetic sword bridge",error); }
    }
    private BlackFoxStandingIaidoVisual() { }
}
