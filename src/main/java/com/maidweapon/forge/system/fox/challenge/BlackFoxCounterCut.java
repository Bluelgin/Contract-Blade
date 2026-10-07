package com.maidweapon.forge.system.fox.challenge;

import net.minecraft.server.level.ServerPlayer;

/** Reactive guard is immediate; its answer is delayed, committed and can itself be parried or dodged. */
final class BlackFoxCounterCut {
    static void tick(BlackFoxActions actions, ServerPlayer player) {
        if (actions.fight.age() == 2) BlackFoxEffects.tell(actions.boss(), true);
        if (actions.fight.age() == 10) actions.cut(player, 6, 6, -65);
    }
    private BlackFoxCounterCut() { }
}
