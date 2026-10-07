package com.maidweapon.forge.system.fox.challenge;

import net.minecraft.server.level.ServerPlayer;

/** Two committed cuts 30 ticks apart: each has its own tell, aim and fresh parry opportunity. */
final class BlackFoxCrossCuts {
    static void tick(BlackFoxActions actions, ServerPlayer player) {
        int age = actions.fight.age();
        actions.stopHorizontal();
        if (age == 6 || age == 36) { actions.commit(player); BlackFoxEffects.tell(actions.boss(), true); }
        if (age == 14 || age == 44) actions.cut(player, 8, 6, age == 14 ? 45 : -45);
    }
    private BlackFoxCrossCuts() { }
}
