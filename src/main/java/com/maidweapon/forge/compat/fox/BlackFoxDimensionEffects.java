package com.maidweapon.forge.compat.fox;

import com.maidweapon.forge.entity.BlackFoxBossEntity;
import com.maidweapon.forge.system.fox.challenge.BlackFoxFight;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import static com.maidweapon.forge.system.fox.challenge.BlackFoxDimensionTimeline.*;

/** Pure render-time sampling. No entity spawning, combat tests or frame-by-frame accumulated state. */
final class BlackFoxDimensionEffects {
    static void draw(BlackFoxBossEntity boss, float partial, PoseStack stack, MultiBufferSource buffers) {
        if (boss.skill() != BlackFoxFight.Skill.DIMENSION_STRIKE) return;
        if (boss.skillAge(partial)>=HIT) return; // The cut is drawn from the synchronized world-space snapshot.
        sample(boss.skillAge(partial), boss.strikeSink(), boss.strikeEmerge(), boss.strikeYaw(), stack, buffers,
                net.minecraft.client.Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
    }
    static void sample(float age, Vec3 sink, Vec3 emerge, float yaw, PoseStack stack, MultiBufferSource buffers,
                       org.joml.Quaternionf cameraOrientation) {
        if (age < 0 || age >= END) return;
        Vec3 base = age < EMERGE ? sink : emerge;
        if (age < DIVE_END+4) BlackFoxRiftVisual.draw(stack, buffers, sink.subtract(base),
                1.4f, (float)Math.sin(Math.PI*progress(age,0,DIVE_END+4)), true, cameraOrientation,age);
        if (age >= WARNING && age < HIT) BlackFoxRiftVisual.draw(stack, buffers, emerge.subtract(base).add(0,1.1,0),
                .2f+progress(age,WARNING,WARNING+2)*1.8f, 1-progress(age,EMERGE,EMERGE+4), false, cameraOrientation,age);
        // Windup belongs to the bone-bound iaido charge, not an outward slash preview.
        if (age >= HIT) BlackFoxArcSlashVisual.draw(stack, buffers, yaw,age-HIT,
                .85f+progress(age,HIT,HIT+2)*.3f,-12,1);
    }
    private BlackFoxDimensionEffects() { }
}
