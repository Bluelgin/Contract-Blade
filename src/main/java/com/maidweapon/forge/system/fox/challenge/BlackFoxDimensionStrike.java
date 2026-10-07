package com.maidweapon.forge.system.fox.challenge;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;
import static com.maidweapon.forge.system.fox.challenge.BlackFoxDimensionTimeline.*;

/** One committed, parryable strike. Presentation and its resource lifetime are not combat entities. */
public final class BlackFoxDimensionStrike {
    private final BlackFoxActions actions;
    private Vec3 sink = Vec3.ZERO, emerge = Vec3.ZERO;
    private float yaw;
    BlackFoxDimensionStrike(BlackFoxActions actions) { this.actions = actions; }
    public Vec3 sink() { return sink; }
    public Vec3 emerge() { return emerge; }
    public float yaw() { return yaw; }
    void tick(ServerPlayer player) {
        var boss = actions.boss();
        int age = actions.fight.age();
        if (age == 1) {
            sink = boss.position();
            emerge = sink;
            boss.playSound(SoundEvents.ENDERMAN_TELEPORT, .55f, .7f);
        }
        if (age == WARNING) {
            // Choose late, from the current facing: no long-lived marker revealing the ambush.
            Vec3 front = BlackFoxActions.horizontal(player.getLookAngle()).normalize();
            if (front.lengthSqr() < .01) front = new Vec3(0, 0, -1);
            emerge = sink;
            int side = boss.getRandom().nextBoolean() ? 1 : -1;
            int offset = boss.getRandom().nextInt(25);
            for (int angle : new int[]{side*(100+offset),-side*(115+offset),side*160,side*70}) {
                Vec3 candidate = actions.bounded(player.position().add(front.yRot((float)Math.toRadians(angle)).scale(6.5)));
                if (BlackFoxActions.horizontal(candidate.subtract(player.position())).length() >= 4
                        && boss.level().noCollision(boss,boss.getBoundingBox().move(candidate.subtract(sink)))) {
                    emerge = candidate; break;
                }
            }
            // Directional breach cue leaves a reaction window even when outside the camera.
            boss.level().playSound(null, emerge.x, emerge.y, emerge.z,
                    SoundEvents.ENDERMAN_TELEPORT, boss.getSoundSource(), 1.2f, 1.5f);
        }
        if (age == EMERGE) {
            if (BlackFoxActions.horizontal(player.position().subtract(emerge)).length() < 3
                    || !boss.level().noCollision(boss,boss.getBoundingBox().move(emerge.subtract(boss.position())))) {
                actions.fight.start(BlackFoxFight.Skill.APPROACH); return;
            }
            boss.teleportTo(emerge.x, emerge.y, emerge.z);
            boss.playSound(SoundEvents.ENDERMAN_TELEPORT, .8f, .6f);
        }
        if (age >= EMERGE && age <= LOCK) {
            actions.commit(player); yaw = boss.getYRot();
        }
        if (age == HIT) {
            actions.markSlash();
            boss.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.3f, .55f);
            Vec3 offset = player.position().subtract(boss.position());
            if (Math.abs(offset.y) > 2.8 || BlackFoxActions.horizontal(offset).lengthSqr() > 8.5*8.5
                    || actions.committed.dot(BlackFoxActions.horizontal(offset).normalize()) < .5
                    || actions.protectedPlayer(player)) return;
            if (actions.parry(player)) {
                actions.fight.clash();
                BlackFoxEffects.breakMomentum(boss);
            } else player.hurt(boss.damageSources().mobAttack(boss), 18);
        }
    }
}
