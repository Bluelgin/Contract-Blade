package com.maidweapon.forge.compat.fox;

import com.maidweapon.forge.entity.BlackFoxBossEntity;
import com.maidweapon.forge.system.fox.challenge.BlackFoxSwordFormation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;

/** Reuses the dimensional-strike texture and energy shader, sampled from server formation state. */
final class BlackFoxSwordRifts {
    static void draw(BlackFoxBossEntity boss, float partial, PoseStack stack, MultiBufferSource buffers) {
        int mask = boss.swordsMask();
        boolean wheel = (mask & BlackFoxSwordFormation.WHEEL_FLAG) != 0;
        boolean arc = (mask & 32) != 0;
        if (mask == 0) return;
        float age = boss.level().getGameTime() - boss.swordsStartedAt() + partial;
        if (age < 0 || age > (wheel ? 60 : 28)) return;
        int hold = boss.phaseTwo() ? BlackFoxSwordFormation.SECOND_HOLD : BlackFoxSwordFormation.FIRST_HOLD;
        var camera = net.minecraft.client.Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation();
        for (int slot = 0; slot < (wheel ? 12 : 5); slot++) {
            if ((mask & (1 << slot)) == 0) continue;
            float opening = Math.min(1, age / 4);
            int fire = wheel ? BlackFoxSwordFormation.wheelFireAge(slot) : BlackFoxSwordFormation.fireAge(slot, boss.phaseTwo());
            float closing = Math.min(1, Math.max(0, (fire + 1 - age) / 3));
            float flash = age >= (wheel ? BlackFoxSwordFormation.WHEEL_STOP : hold - BlackFoxSwordFormation.LOCK_LEAD) ? 1 : .6f;
            var offset = wheel ? BlackFoxSwordFormation.wheelOffset(slot, boss.swordsYaw(), age)
                    : arc ? BlackFoxSwordFormation.arcOffset(slot, boss.swordsYaw())
                    : BlackFoxSwordFormation.offset(slot, boss.swordsYaw());
            BlackFoxRiftVisual.draw(stack, buffers, offset, .48f * opening,
                    opening * closing * flash, false, camera, age + slot * 2);
        }
    }
    private BlackFoxSwordRifts() { }
}
