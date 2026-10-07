package com.maidweapon.forge.system.fox.challenge;

import com.maidweapon.forge.compat.BlackFoxSlashCompat;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/** Each named handler owns only one skill; adding a skill does not change damage/session ownership. */
final class BlackFoxSkills {
    static void tick(BlackFoxActions actions, ServerPlayer player) {
        var fight = actions.fight;
        int age = fight.age();
        switch (fight.skill()) {
            case RETURN_BLADE -> pursuit(actions, player, age);
            case SKY -> sky(actions, player, age);
            case SEAL -> seal(actions, player, age);
            case UNSEAL -> unseal(actions, player, age);
            case STAGGER -> stagger(actions, age);
            default -> { }
        }
    }
    /** Native B attacks from the committed position; only phase one ends at the B3 boundary. */
    private static void pursuit(BlackFoxActions actions, ServerPlayer player, int age) {
        var fight = actions.fight;
        var boss = actions.boss();
        actions.face(player.position());
        if (age == 1) BlackFoxEffects.tell(boss, true);
        actions.nativeCombo().tick();
        actions.stopHorizontal();
        if (!fight.phaseTwo() && actions.nativeCombo().finished()) fight.endRush();
    }
    private static void sky(BlackFoxActions actions, ServerPlayer player, int age) {
        Mob boss = actions.boss();
        if (age <= 7) actions.face(player.position());
        if (age == 8) { actions.commit(player); BlackFoxEffects.tell(boss, true); }
        if (age == 22) actions.slash(player, 11, 6, 90);
        if (age >= 26) actions.stopHorizontal();

    }
    private static void seal(BlackFoxActions actions, ServerPlayer player, int age) {
        Mob boss = actions.boss();
        actions.stopHorizontal();
        if (age == 6) {
            actions.commit(player);
            Vec3 side = new Vec3(-actions.committed.z, 0, actions.committed.x);
            Vec3 center = player.position().add(actions.committed.scale(2));
            actions.laneStart = actions.bounded(center.add(side.scale(-7))); actions.laneEnd = actions.bounded(center.add(side.scale(7)));
            BlackFoxEffects.tell(boss, false);
        }
        if (age >= 8 && age <= 25 && age % 4 == 0) BlackFoxEffects.line((ServerLevel) boss.level(), actions.laneStart, actions.laneEnd, true);
        if (age == 27) {
            BlackFoxSlashCompat.slash(boss, 0);
            BlackFoxEffects.line((ServerLevel) boss.level(), actions.laneStart, actions.laneEnd, false);
            if (player.getY() - actions.laneStart.y < 2 && actions.distanceToLane(player.position()) < 1.1)
                player.hurt(boss.damageSources().mobAttack(boss), 10);
        }

    }
    private static void stagger(BlackFoxActions actions, int age) {
        // Only the Boss pauses; the player and the server remain fully responsive.
        if (age > 4) actions.stopHorizontal();

    }
    /** Reuses the existing warning/hit geometry, with three separately telegraphed lanes. */
    private static void unseal(BlackFoxActions actions, ServerPlayer player, int age) {
        var boss = actions.boss();
        actions.stopHorizontal();
        for (int wave = 0; wave < 3; wave++) {
            int start = 6 + wave * 18;
            if (age == start) {
                actions.commit(player);
                Vec3 direction = actions.committed.yRot((float) Math.toRadians((wave - 1) * 55));
                Vec3 center = player.position();
                actions.laneStart = actions.bounded(center.subtract(direction.scale(8)));
                actions.laneEnd = actions.bounded(center.add(direction.scale(8)));
                BlackFoxEffects.tell(boss, true);
            }
            if (age >= start + 2 && age <= start + 12 && (age - start) % 3 == 0)
                BlackFoxEffects.line((ServerLevel) boss.level(), actions.laneStart, actions.laneEnd, true);
            if (age == start + 14) {
                BlackFoxSlashCompat.slash(boss, (wave - 1) * 55);
                BlackFoxEffects.line((ServerLevel) boss.level(), actions.laneStart, actions.laneEnd, false);
                if (Math.abs(player.getY() - actions.laneStart.y) < 2 && actions.distanceToLane(player.position()) < 1.1)
                    player.hurt(boss.damageSources().mobAttack(boss), 12);
            }
        }
    }
    private BlackFoxSkills() { }
}

