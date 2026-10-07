package com.maidweapon.forge.compat.fox;

import com.maidweapon.forge.entity.BlackFoxBossEntity;
import com.maidweapon.forge.system.fox.challenge.BlackFoxFight;
import com.maidweapon.forge.system.fox.challenge.BlackFoxSlashPresentation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;

/** One synchronized cut snapshot, fixed in world space even after skill changes or teleportation. */
final class BlackFoxSlashEffects {
    static void draw(BlackFoxBossEntity boss,float partial,PoseStack stack,MultiBufferSource buffers) {
        if(boss.slashBorn()==Long.MIN_VALUE || boss.skill()==BlackFoxFight.Skill.DEFEATED) return;
        var kind=boss.slashKind();
        float elapsed=boss.level().getGameTime()-boss.slashBorn()+partial;
        float age=BlackFoxSlashPresentation.melee(kind) ? elapsed : BlackFoxSlashPresentation.visualAge(elapsed,
                BlackFoxSlashPresentation.speed(kind));
        float lifetime=BlackFoxSlashPresentation.melee(kind) ? BlackFoxSlashPresentation.MELEE_END : BlackFoxSlashPresentation.LIFETIME;
        if(age<0 || age>=lifetime) return;
        // EntityRenderDispatcher uses interpolated coordinates, not boss.position(). Cancel exactly that transform.
        Vec3 rendered=new Vec3(net.minecraft.util.Mth.lerp(partial,boss.xOld,boss.getX()),
                net.minecraft.util.Mth.lerp(partial,boss.yOld,boss.getY()),
                net.minecraft.util.Mth.lerp(partial,boss.zOld,boss.getZ()));
        Vec3 delta=boss.slashOrigin().subtract(rendered);
        stack.pushPose();
        try {
            stack.translate(delta.x,delta.y,delta.z);
            if(kind==BlackFoxFight.Skill.STANDING_IAIDO) BlackFoxArenaSlashVisual.draw(stack,buffers,age,boss.slashArenaRadius());
            else if(BlackFoxSlashPresentation.melee(kind)) BlackFoxArcSlashVisual.drawMelee(stack,buffers,
                    boss.slashYaw(),elapsed,boss.slashRoll());
            else BlackFoxArcSlashVisual.draw(stack,buffers,boss.slashYaw(),age,
                    BlackFoxSlashPresentation.scale(kind),boss.slashRoll(),kind==BlackFoxFight.Skill.STANDING_IAIDO
                            || kind==BlackFoxFight.Skill.DIMENSION_STRIKE ? 1 : .65f);
        } finally { stack.popPose(); }
    }
    private BlackFoxSlashEffects() { }
}
