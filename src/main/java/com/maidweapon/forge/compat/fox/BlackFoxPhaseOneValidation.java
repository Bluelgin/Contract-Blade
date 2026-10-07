package com.maidweapon.forge.compat.fox;

import com.maidweapon.forge.system.fox.challenge.*;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Opt-in world-time checks of the new attack executors, not just selection math. */
final class BlackFoxPhaseOneValidation {
    static void run(BlackFoxCombatant actor, ServerPlayer player) {
        var boss = actor.body();
        var fight = actor.combat().fight();
        boss.setPos(Vec3.atBottomCenterOf(actor.arenaOrigin()).add(0, 1, 0));
        boss.setDeltaMovement(Vec3.ZERO);
        player.setPos(boss.position().add(0, 0, -4)); player.setYRot(0); boss.setYRot(180);
        tickClock(actor, 30);
        fight.start(BlackFoxFight.Skill.STAGGER); actor.combat().tick();
        fight.start(BlackFoxFight.Skill.APPROACH);
        actor.combat().tick();
        check(boss.getDeltaMovement().horizontalDistance() < .001, "holds normal attack distance without drifting into player");
        player.setPos(boss.position().add(0, 0, -2));
        var beforeBlink = boss.position();
        for (int age = 0; age < 8; age++) {
            tickClock(actor, 1); actor.combat().tick();
            check(boss.position().equals(beforeBlink) && boss.getDeltaMovement().lengthSqr() == 0,
                    "blink warning does not move the boss or hit immediately");
        }
        tickClock(actor, 1); actor.combat().tick();
        check(boss.position().distanceTo(beforeBlink) > 1 && boss.distanceTo(player) >= BlackFoxSpacing.MIN
                        && boss.distanceTo(player)<=BlackFoxSpacing.MAX
                        && frontDot(player,boss.position())>=.5,
                "crowding triggers a discrete warned blink with separation");
        var afterBlink=boss.position();
        fight.start(BlackFoxFight.Skill.SIDESTEP_CUT);
        for(int i=0;i<9;i++) { tickClock(actor,1); actor.combat().tick(); }
        check(boss.position().equals(afterBlink),"side cut cannot bypass the shared blink cooldown");
        fight.start(BlackFoxFight.Skill.STAGGER); tickClock(actor,40); actor.combat().tick();
        fight.start(BlackFoxFight.Skill.SIDESTEP_CUT);
        tickClock(actor, 1); actor.combat().tick();
        var cancelled = boss.position();
        fight.start(BlackFoxFight.Skill.STAGGER);
        for (int i = 0; i < 10; i++) { tickClock(actor, 1); actor.combat().tick(); }
        check(boss.position().equals(cancelled), "interrupted skill cancels pending blink rather than teleporting during stagger");
        player.setPos(actor.arenaOrigin().getX() + 18.5, actor.arenaOrigin().getY() + 1,
                actor.arenaOrigin().getZ() + .5);
        tickClock(actor,40);
        fight.start(BlackFoxFight.Skill.SIDESTEP_CUT);
        for (int i = 0; i < 9; i++) { tickClock(actor, 1); actor.combat().tick(); }
        check(boss.position().distanceTo(cancelled) > 1 && boss.distanceTo(player) >= BlackFoxSpacing.MIN
                        && boss.distanceTo(player)<=BlackFoxSpacing.MAX
                        && frontDot(player,boss.position())>=.5
                        && FoxChallengeArena.contains(actor.arenaOrigin(), boss.getX(), boss.getY(), boss.getZ()),
                "edge-of-arena blink keeps a valid separated landing inside the cell");
        fight.start(BlackFoxFight.Skill.STAGGER); tickClock(actor,40); actor.combat().tick();
        boss.setPos(Vec3.atBottomCenterOf(actor.arenaOrigin()).add(0,1,0));
        player.setPos(boss.position().add(0,0,-8)); player.setYRot(0);
        fight.start(BlackFoxFight.Skill.APPROACH);
        for(int i=0;i<9;i++) { tickClock(actor,1); actor.combat().tick(); }
        check(boss.distanceTo(player)>=BlackFoxSpacing.MIN && boss.distanceTo(player)<=BlackFoxSpacing.MAX
                && frontDot(player,boss.position())>=.5,"escape beyond seven blocks restores forward engagement range");
        fight.start(BlackFoxFight.Skill.STAGGER); tickClock(actor,40); actor.combat().tick();
        boss.setPos(Vec3.atBottomCenterOf(actor.arenaOrigin()).add(0,1,0));
        player.setPos(boss.position().add(0,0,-2)); player.setYRot(0); boss.setYRot(180);
        fight.start(BlackFoxFight.Skill.PROBE_CUT);
        BlackFoxEncounters.swing(player,player.getMainHandItem(),true);
        check(actor.combat().parryNative(player),"real close-range parry triggers feedback hold");
        var feedbackPosition=boss.position();
        fight.start(BlackFoxFight.Skill.APPROACH);
        for(int i=0;i<BlackFoxSpacing.PARRY_HOLD-1;i++) {
            tickClock(actor,1); actor.combat().tick();
            check(boss.position().equals(feedbackPosition) && fight.skill()==BlackFoxFight.Skill.APPROACH,
                    "parry feedback holds position and delays the next attack despite crowding");
        }
        boss.setPos(Vec3.atBottomCenterOf(actor.arenaOrigin()).add(0, 1, 0));
        boss.setDeltaMovement(Vec3.ZERO);
        player.setPos(boss.position().add(0, 0, -4)); player.setYRot(0);
        for (boolean secondParry : new boolean[]{false, true}) {
            tickClock(actor, 30);
            fight.start(BlackFoxFight.Skill.STAGGER); actor.combat().tick();
            player.setHealth(1000); player.invulnerableTime = 0;
            fight.start(BlackFoxFight.Skill.CROSS_CUT);
            for (int age = 1; age <= 44; age++) {
                tickClock(actor, 1);
                player.invulnerableTime = 0;
                if (age == 14 || age == 44 && secondParry)
                    BlackFoxEncounters.swing(player, player.getMainHandItem(), true);
                actor.combat().tick();
                if (age == 14) check(player.getHealth() == 1000 && actor.combat().protects(player), "first committed cut can be parried at four blocks");
            }
            check(secondParry ? player.getHealth() == 1000 : player.getHealth() < 1000,
                    "second cut needs a new swing after the first protection expires");
        }
        tickClock(actor, 30);
        fight.start(BlackFoxFight.Skill.PHANTOM_FEINT);
        for (int age = 0; age < 12; age++) { tickClock(actor, 1); actor.combat().tick(); }
        check(actor.combat().ranged().activeShots() == 3, "first phase spawns only a small three-sword native volley");
        var cuts = ((net.minecraft.server.level.ServerLevel) boss.level()).getEntitiesOfClass(
                com.maidweapon.forge.entity.BlackFoxMinorCutEntity.class, boss.getBoundingBox().inflate(64), cut -> cut.getOwner() == boss);
        check(cuts.size() == 1, "small volley includes exactly one miniature judgement cut");
        var cut = cuts.get(0); var birth = cut.position();
        player.setHealth(1000); player.invulnerableTime = 0;
        for (int i = 0; i < 40 && !cut.isRemoved(); i++) cut.tick();
        check(cut.position().distanceTo(birth) > 1 && cut.isRemoved() && player.getHealth() < 1000,
                "miniature cut really travels, hits its challenger once and disappears");
        fight.start(BlackFoxFight.Skill.STAGGER);
        tickClock(actor, 61); actor.combat().tick();
        check(actor.combat().ranged().activeShots() == 0, "first-phase projectile lifetime is bounded even during recovery");
        fight.start(BlackFoxFight.Skill.APPROACH);
        LogUtils.getLogger().info("BLACK_FOX_PHASE_ONE_PASS: forward 4-5 block spacing, close/far correction, shared blink cooldown, parry hold, dual cuts and native volley expiry");
    }
    private static void tickClock(BlackFoxCombatant actor, int ticks) {
        ((net.minecraft.world.level.storage.ServerLevelData) actor.body().getServer().overworld().getLevelData())
                .setGameTime(actor.body().level().getGameTime() + ticks);
    }
    private static void check(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
    private static double frontDot(ServerPlayer player,Vec3 point) {
            Vec3 look=new Vec3(player.getLookAngle().x,0,player.getLookAngle().z).normalize();
            Vec3 delta=point.subtract(player.position());
            return look.dot(new Vec3(delta.x,0,delta.z).normalize());
    }
}
