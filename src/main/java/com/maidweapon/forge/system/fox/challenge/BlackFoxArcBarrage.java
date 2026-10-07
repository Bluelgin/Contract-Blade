package com.maidweapon.forge.system.fox.challenge;

import net.minecraft.server.level.ServerPlayer;

/** Warned lateral blink followed by two native sword fans and a generous recovery. */
final class BlackFoxArcBarrage {
    static void tick(BlackFoxActions actions, BlackFoxBlink blink, ServerPlayer player) {
        int age = actions.fight.age();
        actions.face(player.position());
        if (age == 1) blink.request(player, true);
        if (age == 18 || age == 50) actions.ranged().arcVolley(player);
    }
    private BlackFoxArcBarrage() { }
}
