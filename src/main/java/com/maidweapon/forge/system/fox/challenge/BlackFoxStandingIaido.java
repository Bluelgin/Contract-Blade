package com.maidweapon.forge.system.fox.challenge;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;
import static com.maidweapon.forge.system.fox.challenge.BlackFoxStandingIaidoTimeline.*;

/** Independent executor; cosmetic gathering never creates damaging projectiles. */
final class BlackFoxStandingIaido {
    static void tick(BlackFoxActions actions, ServerPlayer player) {
        var boss=actions.boss(); int age=actions.fight.age();
        if(age<=LOCK) actions.commit(player);
        if(age==1) boss.playSound(SoundEvents.BEACON_POWER_SELECT,.65f,.55f);
        if(age==16 || age==28) boss.playSound(SoundEvents.AMETHYST_BLOCK_RESONATE,.65f,age==16?.8f:1.2f);
        if(age==LOCK) boss.playSound(SoundEvents.AMETHYST_BLOCK_CHIME,.8f,1.6f);
        if(age!=HIT) return;
        // A short bounded step, not a teleport onto the challenger; do not move through solid blocks.
        Vec3 destination=actions.bounded(boss.position().add(actions.committed.scale(.8)));
        if(boss.level().noCollision(boss,boss.getBoundingBox().move(destination.subtract(boss.position()))))
            boss.setPos(destination.x,destination.y,destination.z);
        boss.playSound(SoundEvents.PLAYER_ATTACK_SWEEP,1.3f,.6f);
        actions.markSlash();
        if(actions.protectedPlayer(player)) return;
        if(actions.parryArena(player)) {
            actions.fight.clash(); BlackFoxEffects.breakMomentum(boss);
        } else player.hurt(boss.damageSources().mobAttack(boss),60);
    }
    private BlackFoxStandingIaido() { }
}
