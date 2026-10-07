package com.maidweapon.forge.compat.fox;

import com.maidweapon.forge.system.fox.challenge.*;
import com.maidweapon.forge.entity.BlackFoxBossEntity;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import static com.maidweapon.forge.system.fox.challenge.BlackFoxDimensionTimeline.*;

/** Real server-time checks: committed landing, one hit, melee parry, synchronization and cancellation. */
final class BlackFoxDimensionValidation {
    static void run(BlackFoxCombatant actor, ServerPlayer player) {
        var boss = actor.body(); var fight = actor.combat().fight();
        for (int variant=0;variant<3;variant++) {
            clock(actor,40); fight.start(BlackFoxFight.Skill.STAGGER); actor.combat().tick();
            boss.setPos(Vec3.atBottomCenterOf(actor.arenaOrigin()).add(0,1,0));
            player.setPos(boss.position().add(0,0,-8)); player.setYRot(0);
            player.setHealth(1000); player.invulnerableTime=0;
            fight.start(BlackFoxFight.Skill.DIMENSION_STRIKE);
            for(int age=1;age<=HIT+5;age++) {
                clock(actor,1);
                if (variant==2 && age==20) fight.start(BlackFoxFight.Skill.STAGGER);
                if (variant==1 && age==HIT) {
                    Vec3 direction = boss.position().subtract(player.position());
                    player.setYRot((float)Math.toDegrees(Math.atan2(-direction.x,direction.z)));
                    BlackFoxEncounters.swing(player,player.getMainHandItem(),true);
                }
                actor.combat().tick();
                if(age==HIT && variant!=2) {
                    var entity=(BlackFoxBossEntity)boss;
                    check(entity.slashKind()==BlackFoxFight.Skill.DIMENSION_STRIKE
                            && entity.slashBorn()==boss.level().getGameTime()
                            && entity.slashOrigin().distanceTo(boss.position())<.001,
                            "cut snapshot matches the emerged body's authoritative hit position");
                }
                if (fight.skill()==BlackFoxFight.Skill.DIMENSION_STRIKE) {
                    var entity=(BlackFoxBossEntity)boss;
                    check(entity.skill()==fight.skill() && entity.skillAge(0)==fight.age(),"skill clock follows authoritative server start");
                    if(submerged(age)) check(actor.combat().intercept(boss.damageSources().playerAttack(player)),"submerged boss cannot be hit");
                    if(age==EMERGE) check(boss.position().distanceTo(actor.combat().dimensionStrike().emerge())<.01,"lands at warned point");
                }
                if(age<HIT) check(player.getHealth()==1000,"no damage during dive/breach/windup");
            }
            if(variant==0) check(player.getHealth()<1000,"committed grand slash deals damage");
            else check(player.getHealth()==1000,"parry or cancellation prevents grand slash damage");
            if(variant==1) check(fight.skill()==BlackFoxFight.Skill.STAGGER && actor.combat().protects(player),"successful parry breaks the strike and protects challenger");
        }
        fight.start(BlackFoxFight.Skill.APPROACH);
        LogUtils.getLogger().info("BLACK_FOX_DIMENSION_PASS: server clock, breach position, submerged immunity, delayed hit, parry and interrupted attack");
    }
    private static void clock(BlackFoxCombatant actor,int ticks) {
        ((net.minecraft.world.level.storage.ServerLevelData)actor.body().getServer().overworld().getLevelData())
                .setGameTime(actor.body().level().getGameTime()+ticks);
    }
    private static void check(boolean condition,String message) { if(!condition)throw new IllegalStateException(message); }
}
