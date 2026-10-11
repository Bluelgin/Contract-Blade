package com.maidweapon.forge.system.fox.challenge;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Three committed forward landings, each followed by a fresh registered native Combo B. */
final class BlackFoxTripleCombo {
    private static final int WARNING = 8, ATTACK = 160, RECOVERY = 4;
    private static final int ROUND = WARNING + ATTACK + RECOVERY;
    static final int DURATION = ROUND * 3 + 4; // Final round gets eight recovery ticks, not four.
    private final BlackFoxActions actions;
    private Vec3 destination;
    private boolean attacking;
    private int round, roundStarted = 1;
    BlackFoxTripleCombo(BlackFoxActions actions) { this.actions = actions; }

    void cancel() {
        destination = null; attacking = false;
        round = 0; roundStarted = 1;
        actions.fight.comboAge(-1);
    }

    void tick(ServerPlayer player) {
        int age = actions.fight.age();
        actions.fight.comboRound(round);
        int local = age - roundStarted + 1;
        actions.stopHorizontal();
        actions.face(player.position());
        if (local < 1) return;
        if (round >= 3) { actions.fight.finishTripleCombo(); return; }
        if (local == 1) {
            actions.nativeCombo().stop(); destination = null; attacking = false;
            actions.fight.comboAge(-1);
            destination = landing(player, round);
            if (destination != null) {
                BlackFoxEffects.blink(actions.boss(), destination, false);
                BlackFoxEffects.tell(actions.boss(), true);
            }
        }
        if (local == WARNING + 1 && destination != null) {
            double distance = BlackFoxActions.horizontal(destination.subtract(player.position())).length();
            // The marker is fixed: dodging it never causes a silent retarget or a replacement landing.
            if (distance >= BlackFoxSpacing.CLOSE && distance <= BlackFoxSpacing.FAR
                    && Math.abs(player.getY() - destination.y) <= 2.4
                    && actions.boss().level().noCollision(actions.boss(), actions.boss().getBoundingBox()
                            .move(destination.subtract(actions.boss().position())))) {
                BlackFoxEffects.blink(actions.boss(), destination, true);
                actions.boss().teleportTo(destination.x, destination.y, destination.z);
                actions.face(player.position());
                attacking = true;
            }
            destination = null;
        }
        if (attacking && local > WARNING) {
            actions.fight.comboAge(local - WARNING);
            actions.nativeCombo().tick();
            // A native timeout ends this round; never restart B1 inside a finished round.
            // A fifth parry can change the skill while a native callback is running.
            if (actions.fight.skill() != BlackFoxFight.Skill.TRIPLE_COMBO) return;
            if (!actions.nativeCombo().finished() && local < WARNING + ATTACK) return;
        }
        if (local > WARNING) nextRound(age);
    }

    private void nextRound(int age) {
        actions.nativeCombo().stop(); destination = null; attacking = false;
        actions.fight.comboAge(-1);
        round++; roundStarted = age + (round == 3 ? 8 : RECOVERY) + 1;
    }

    private Vec3 landing(ServerPlayer player, int round) {
        Vec3 front = BlackFoxActions.horizontal(player.getLookAngle()).normalize();
        if (front.lengthSqr() < .01) {
            double yaw = Math.toRadians(player.getYRot());
            front = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        }
        double angle = (round - 1) * 50;
        Vec3 candidate = actions.bounded(player.position().add(front.yRot((float) Math.toRadians(angle))
                .scale(BlackFoxSpacing.TARGET)));
        Vec3 offset = BlackFoxActions.horizontal(candidate.subtract(player.position()));
        if (offset.length() < BlackFoxSpacing.MIN || offset.length() > BlackFoxSpacing.MAX
                || front.dot(offset.normalize()) < .5
                || !actions.boss().level().noCollision(actions.boss(), actions.boss().getBoundingBox()
                        .move(candidate.subtract(actions.boss().position())))) return null;
        return candidate;
    }
}
