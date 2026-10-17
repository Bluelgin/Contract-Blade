package com.maidweapon.forge.system.fox.challenge;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;
import static com.maidweapon.forge.system.fox.challenge.BlackFoxDimensionTimeline.*;

/** Three fast, individually warned strikes; server and renderer share the same round clock. */
public final class BlackFoxDimensionStrike {
    private final BlackFoxActions actions;
    private Vec3 sink = Vec3.ZERO, emerge = Vec3.ZERO;
    private float yaw;
    private boolean destinationReady, landed;
    BlackFoxDimensionStrike(BlackFoxActions actions) { this.actions = actions; }
    public Vec3 sink() { return sink; }
    public Vec3 emerge() { return emerge; }
    public float yaw() { return yaw; }
    void tick(ServerPlayer player) {
        var boss = actions.boss();
        int globalAge = actions.fight.age();
        int age = (int) localAge(globalAge);
        int round = (globalAge - 1) / ROUND;
        if (age == 1) {
            sink = boss.position();
            emerge = sink;
            destinationReady = false; landed = false;
            yaw = boss.getYRot();
            boss.playSound(SoundEvents.ENDERMAN_TELEPORT, .55f, .7f);
        }
        if (age == WARNING) {
            // Choose late, from the current facing: no long-lived marker revealing the ambush.
            Vec3 front = BlackFoxActions.horizontal(player.getLookAngle()).normalize();
            if (front.lengthSqr() < .01) front = new Vec3(0, 0, -1);
            emerge = sink;
            int side = round % 2 == 0 ? 1 : -1;
            int offset = boss.getRandom().nextInt(25);
            for (int angle : new int[]{side*(100+offset),-side*(115+offset),side*160,side*70}) {
                Vec3 candidate = actions.bounded(player.position().add(front.yRot((float)Math.toRadians(angle)).scale(6.5)));
                if (BlackFoxActions.horizontal(candidate.subtract(player.position())).length() >= 4
                        && boss.level().noCollision(boss,boss.getBoundingBox().move(candidate.subtract(sink)))) {
                    emerge = candidate; destinationReady = true; break;
                }
            }
            // Directional breach cue leaves a reaction window even when outside the camera.
            boss.level().playSound(null, emerge.x, emerge.y, emerge.z,
                    SoundEvents.ENDERMAN_TELEPORT, boss.getSoundSource(), 1.2f, 1.5f);
            if (destinationReady) {
                Vec3 direction = player.position().subtract(emerge);
                yaw = (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
                BlackFoxEffects.blink(boss, emerge, false);
                boss.level().playSound(null, emerge.x, emerge.y, emerge.z,
                        SoundEvents.AMETHYST_BLOCK_BREAK, boss.getSoundSource(), .45f, .65f);
            }
        }
        if (age == EMERGE) {
            double distance = BlackFoxActions.horizontal(player.position().subtract(emerge)).length();
            if (!destinationReady || distance < 3 || distance > 10 || Math.abs(player.getY() - emerge.y) > 2.8
                    || !boss.level().noCollision(boss,boss.getBoundingBox().move(emerge.subtract(boss.position())))) {
                return; // Skip an unsafe round without silently choosing a different landing.
            }
            boss.teleportTo(emerge.x, emerge.y, emerge.z);
            landed = true;
            boss.playSound(SoundEvents.ENDERMAN_TELEPORT, .8f, .6f);
            boss.playSound(SoundEvents.GLASS_BREAK, .35f, .7f);
        }
        if (landed && age >= EMERGE && age <= lockAge(globalAge)) {
            actions.commit(player); yaw = boss.getYRot();
        }
        if (landed && heavy(globalAge) && age == lockAge(globalAge)) BlackFoxEffects.tell(boss, true);
        if (landed && age == hitAge(globalAge) - 4) BlackFoxEffects.ready(boss, emerge);
        if (landed && age == hitAge(globalAge)) {
            actions.markSlash();
            if (heavy(globalAge)) {
                BlackFoxEffects.heavyStrike(boss);
                ((BlackFoxCombatant) boss).combat().impact(4);
            }
            boss.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.3f, .55f);
            Vec3 offset = player.position().subtract(boss.position());
            if (Math.abs(offset.y) > 2.8 || BlackFoxActions.horizontal(offset).lengthSqr() > 8.5*8.5
                    || actions.committed.dot(BlackFoxActions.horizontal(offset).normalize()) < .5
                    || actions.protectedPlayer(player)) return;
            if (!actions.parry(player)) player.hurt(boss.damageSources().mobAttack(boss), BlackFoxDamageTuning.DIMENSION);
        }
    }
}
