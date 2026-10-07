package com.maidweapon.forge.compat.fox;

import com.maidweapon.forge.system.fox.challenge.*;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import static com.maidweapon.forge.system.fox.challenge.BlackFoxStandingIaidoTimeline.*;

/** Actual server execution: safe charge, arena-wide hit, parry and cancellation. */
final class BlackFoxStandingIaidoValidation {
    static void run(BlackFoxCombatant actor,ServerPlayer player) {
        var boss=actor.body(); var fight=actor.combat().fight();
        for(int variant=0;variant<5;variant++) {
            clock(actor,40); fight.start(BlackFoxFight.Skill.STAGGER); actor.combat().tick();
            boss.setPos(Vec3.atBottomCenterOf(actor.arenaOrigin()).add(0,1,0));
            player.setPos(boss.position().add(0,0,-6)); player.setYRot(0);
            player.setHealth(1000); player.invulnerableTime=0;
            fight.start(BlackFoxFight.Skill.STANDING_IAIDO);
            for(int age=1;age<=HIT+5;age++) {
                clock(actor,1);
                if(variant==3 && age==20) fight.start(BlackFoxFight.Skill.STAGGER);
                if(variant==2 && age==LOCK+1) player.setPos(Vec3.atBottomCenterOf(actor.arenaOrigin()).add(22,1,22));
                if(variant==4 && age==LOCK+1) {
                    player.setPos(Vec3.atBottomCenterOf(actor.arenaOrigin()).add(22,1,22)); player.setYRot(180);
                }
                if((variant==1 || variant==4) && age==HIT) BlackFoxEncounters.swing(player,player.getMainHandItem(),true);
                actor.combat().tick();
                if(age==HIT && variant!=3) {
                    var entity=(com.maidweapon.forge.entity.BlackFoxBossEntity)boss;
                    if(entity.slashKind()!=BlackFoxFight.Skill.STANDING_IAIDO
                            || entity.slashBorn()!=boss.level().getGameTime()
                            || entity.slashOrigin().distanceTo(boss.position())>.001)
                        throw new IllegalStateException("Iaido cut snapshot must match actual hit position even when parried");
                }
                if(age<HIT && player.getHealth()!=1000) throw new IllegalStateException("Damage during iaido charge");
            }
            if(variant==0 || variant==2 ? player.getHealth()>=1000 : player.getHealth()!=1000)
                throw new IllegalStateException("Arena iaido hit/parry/no-dodge/cancel mismatch: " + variant);
            if((variant==1 || variant==4) && (fight.skill()!=BlackFoxFight.Skill.STAGGER || !actor.combat().protects(player)))
                throw new IllegalStateException("Iaido parry must stagger and protect");
            if(variant==0) {
                var entity=(com.maidweapon.forge.entity.BlackFoxBossEntity)boss;
                Vec3 fixed=entity.slashOrigin(),before=boss.position();
                boss.setPos(before.add(9,0,0));
                if(entity.slashOrigin().distanceTo(fixed)>.001)
                    throw new IllegalStateException("Teleport dragged the previous cut snapshot");
                boss.setPos(before);
            }
        }
        fight.start(BlackFoxFight.Skill.APPROACH);
        LogUtils.getLogger().info("BLACK_FOX_STANDING_IAIDO_PASS: long safe charge, arena-corner hit, parry protection, cancellation and teleport-stable cut snapshot");
    }
    private static void clock(BlackFoxCombatant actor,int ticks) {
        ((net.minecraft.world.level.storage.ServerLevelData)actor.body().getServer().overworld().getLevelData())
                .setGameTime(actor.body().level().getGameTime()+ticks);
    }
    private BlackFoxStandingIaidoValidation() { }
}
