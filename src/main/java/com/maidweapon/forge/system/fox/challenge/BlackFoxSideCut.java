package com.maidweapon.forge.system.fox.challenge;

import net.minecraft.server.level.ServerPlayer;

/** Warned blink, a pause after materializing, then a committed return cut and punishable recovery. */
final class BlackFoxSideCut {
    static void tick(BlackFoxActions actions, BlackFoxBlink blink, ServerPlayer player) {
        int age = actions.fight.age();
        if (age == 1) blink.request(player, true);
        if (age == 12) { actions.commit(player); BlackFoxEffects.tell(actions.boss(), true); }
        if (age == 20) actions.cut(player, 9, 6, -55);
    }
    private BlackFoxSideCut() { }
}
