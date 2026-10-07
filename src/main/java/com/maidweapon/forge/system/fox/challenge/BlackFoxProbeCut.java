package com.maidweapon.forge.system.fox.challenge;

import net.minecraft.server.level.ServerPlayer;

/** A single readable opening asks the player to commit before selecting the next exchange. */
final class BlackFoxProbeCut {
    static void tick(BlackFoxActions actions, ServerPlayer player) {
        if (actions.fight.age() == 4) { actions.commit(player); BlackFoxEffects.tell(actions.boss(), true); }
        if (actions.fight.age() == 12) actions.cut(player, 6, 6, 40);
    }
    private BlackFoxProbeCut() { }
}
