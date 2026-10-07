package com.maidweapon.forge.system.fox.challenge;

import net.minecraft.server.level.ServerPlayer;

/** A small native volley, followed by a warned relocation rather than walking after the player. */
final class BlackFoxPhantomFeint {
    static void tick(BlackFoxActions actions, BlackFoxBlink blink, BlackFoxMinorCuts minorCuts, ServerPlayer player) {
        int age = actions.fight.age();
        actions.face(player.position());
        if (age == 1) BlackFoxEffects.tell(actions.boss(), false);
        if (age == 12) {
            actions.ranged().volley(player);
            minorCuts.launch(player);
        }
        if (age == 17) blink.request(player, false);
    }
    private BlackFoxPhantomFeint() { }
}
