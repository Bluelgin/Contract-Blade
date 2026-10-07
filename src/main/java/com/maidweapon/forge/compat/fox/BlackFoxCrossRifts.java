package com.maidweapon.forge.compat.fox;

import com.maidweapon.forge.entity.BlackFoxBossEntity;
import com.maidweapon.forge.system.fox.challenge.BlackFoxFight;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;

/** Reused luminous portals; each closes immediately after its committed drive cut. */
final class BlackFoxCrossRifts {
    static void draw(BlackFoxBossEntity boss, float partial, PoseStack stack, MultiBufferSource buffers) {
        boolean domain = boss.skill() == BlackFoxFight.Skill.DOMAIN_CROSS;
        if (!domain && boss.skill() != BlackFoxFight.Skill.RIFT_CROSS) return;
        float age = boss.skillAge(partial);
        var base = boss.position();
        var camera = net.minecraft.client.Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation();
        for (int i = 0; i < 2; i++) {
            int fire = domain ? (i == 0 ? 32 : 48) : (i == 0 ? 24 : 40);
            if (age < 0 || age >= fire + 4) continue;
            float alpha = Math.min(1, age / 6) * Math.max(0, Math.min(1, (fire + 4 - age) / 4));
            BlackFoxRiftVisual.draw(stack, buffers, (i == 0 ? boss.crossLeft() : boss.crossRight()).subtract(base),
                    domain ? 1.3f : .85f, alpha * (age >= fire - 8 ? 1 : .55f), false, camera, age + i * 7);
        }
    }
    private BlackFoxCrossRifts() { }
}
