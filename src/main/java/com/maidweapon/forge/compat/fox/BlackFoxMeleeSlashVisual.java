package com.maidweapon.forge.compat.fox;

import com.maidweapon.forge.system.fox.challenge.BlackFoxFight;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;

/** Small versions of the shared rift slash, sampled only at the server skill's actual cut windows. */
final class BlackFoxMeleeSlashVisual {
    static void sample(BlackFoxFight.Skill skill,float age,float yaw,PoseStack stack,MultiBufferSource buffers) {
        switch (skill) {
            case PROBE_CUT -> cut(stack,buffers,yaw,age-12,40);
            case COUNTER_CUT -> cut(stack,buffers,yaw,age-10,-65);
            case SIDESTEP_CUT -> cut(stack,buffers,yaw,age-20,-55);
            case CROSS_CUT -> {
                cut(stack,buffers,yaw,age-14,45);
                cut(stack,buffers,yaw,age-44,-45);
            }
            default -> { } // Native Combo B retains its own purple SlashBlade effects.
        }
    }
    private static void cut(PoseStack stack,MultiBufferSource buffers,float yaw,float elapsed,float roll) {
        BlackFoxArcSlashVisual.drawMelee(stack,buffers,yaw,elapsed,roll*.35f);
    }
    private BlackFoxMeleeSlashVisual() { }
}
