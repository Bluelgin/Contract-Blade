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
        sampleAt(boss.skillAge(partial), boss.strikeSink(), boss.strikeEmerge(), boss.strikeYaw(), stack, buffers,
                boss.position());
    }
    static void sample(float age, Vec3 sink, Vec3 emerge, float yaw, PoseStack stack, MultiBufferSource buffers,
                       org.joml.Quaternionf cameraOrientation) {
        if (age < 0 || age >= END) return;
        sampleAt(age, sink, emerge, yaw, stack, buffers, localAge(age) < EMERGE ? sink : emerge);
    }
    private static void sampleAt(float age, Vec3 sink, Vec3 emerge, float yaw, PoseStack stack,
                                 MultiBufferSource buffers, Vec3 base) {
        if (age < 0 || age >= END) return;
        int hit = hitAge(age);
        age = localAge(age);
        if (age < DIVE_END + 5) BlackFoxRiftVisual.tear(stack, buffers, sink.subtract(base), yaw,
                true, Math.min(23, age / (DIVE_END + 5) * 23), .9f);
        if (age >= WARNING && age < hit + 2) {
            // Thin directional crack first; opening is reserved for the emergence, then rapidly seals.
            float frame = age < EMERGE ? 1 + (age - WARNING) / (EMERGE - WARNING) * 5
                    : age < EMERGE + 3 ? 6 + (age - EMERGE) / 3 * 9
                    : age < hit - 5 ? 15 : 15 + (age - hit + 5) / 7 * 8;
            BlackFoxRiftVisual.tear(stack, buffers, emerge.subtract(base).add(0, 1.3, 0), yaw, false, frame, 1);
        }
        // Actual cuts remain exclusively owned by the synchronized slash snapshot renderer.
    }
    private BlackFoxDimensionEffects() { }
}
