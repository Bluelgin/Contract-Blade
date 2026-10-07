package com.maidweapon.forge.compat.fox;

import com.maidweapon.forge.system.fox.challenge.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Actual executors and accepted-damage openings, independent of knowing a special player input. */
final class BlackFoxNewSkillsValidation {
    static void run(BlackFoxCombatant actor, ServerPlayer player) {
        var boss = actor.body(); var fight = actor.combat().fight(); var ranged = actor.combat().ranged();
        ranged.clear();
        boss.setPos(Vec3.atBottomCenterOf(actor.arenaOrigin()).add(0,1,0));
        player.setPos(boss.position().add(0,0,-6)); player.setHealth(1000);
        fight.start(BlackFoxFight.Skill.RIFT_CROSS);
        for (int age = 1; age <= 40; age++) {
            clock(actor,1); actor.combat().tick();
            if (age == 23) check(ranged.activeShots() == 0, "cross portals warn before damaging cuts exist");
            if (age == 24) check(ranged.activeShots() == 1, "first native cross cut");
        }
        check(ranged.activeShots() == 2, "second native cut follows sixteen ticks later");
        check(actor.combat().riftCross().left().distanceTo(actor.combat().riftCross().right()) > 4,
                "cross cuts have distinct committed portal positions");
        for (var skill : new BlackFoxFight.Skill[]{BlackFoxFight.Skill.RIFT_CROSS, BlackFoxFight.Skill.ARC_BARRAGE}) {
            ranged.clear(); clock(actor,30);
            fight.start(skill);
            for (int age=0; age<12; age++) { clock(actor,1); actor.combat().tick(); }
            player.setPos(boss.position().add(0,0,-4)); boss.invulnerableTime=0;
            check(boss.hurt(boss.damageSources().playerAttack(player), 2), "ordinary hit actually lands during preparation");
            check(fight.skill() == BlackFoxFight.Skill.STAGGER && actor.combat().protects(player),
                    "ordinary attack discovers counter without special input and grants safe follow-up");
            check(ranged.formation().mask() == 0 && ranged.activeShots() == 0, "counter clears pending attacks");
        }
        clock(actor,30); ranged.clear();
        fight.start(BlackFoxFight.Skill.RIFT_CROSS);
        for (int age=0;age<12;age++) { clock(actor,1); actor.combat().tick(); }
        boss.invulnerableTime=0;
        var incoming = (net.minecraft.world.entity.projectile.Projectile) net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                .get(net.minecraft.resources.ResourceLocation.parse("slashblade:abstract_summoned_sword")).create(boss.level());
        incoming.setOwner(player);
        com.maidweapon.forge.compat.BlackFoxPhantomCompat.impact(incoming,boss);
        check(fight.skill()==BlackFoxFight.Skill.STAGGER, "ordinary native phantom hit can interrupt an exposed cast");
        incoming.discard();
        clock(actor,30); ranged.clear();
        fight.start(BlackFoxFight.Skill.RIFT_CROSS);
        player.setPos(boss.position().add(0,0,-4)); player.setYRot(0); boss.setYRot(180);
        for (int age=0;age<24;age++) { clock(actor,1); actor.combat().tick(); }
        var cut = ((net.minecraft.server.level.ServerLevel) boss.level()).getEntitiesOfClass(
                net.minecraft.world.entity.projectile.Projectile.class,boss.getBoundingBox().inflate(32),ranged::owns).get(0);
        BlackFoxEncounters.swing(player,player.getMainHandItem(),true);
        float health=player.getHealth();
        com.maidweapon.forge.event.BlackFoxCombatEvents.phantomImpact(new net.minecraftforge.event.entity.ProjectileImpactEvent(
                cut,new net.minecraft.world.phys.EntityHitResult(player)));
        check(fight.skill()==BlackFoxFight.Skill.STAGGER && player.getHealth()==health && ranged.activeShots()==0,
                "normal parry stops cross cut and cancels the second attack");
        clock(actor,30); ranged.clear();
        fight.start(BlackFoxFight.Skill.ARC_BARRAGE);
        for (int age=1;age<=78;age++) {
            clock(actor,1); actor.combat().tick();
            if (age==18 || age==50) check((ranged.formation().mask() & 31) == 31
                    && (ranged.formation().mask() & 32) != 0, "two bounded five-sword fans use the arc layout");
            if (age==46) check(ranged.formation().mask()==0, "first fan fully releases before the second warning");
        }
        check(BlackFoxOpenings.active(fight.skill(), fight.age()), "fan recovery leaves a normal-attack opening");
        ranged.clear(); fight.start(BlackFoxFight.Skill.APPROACH);
        com.mojang.logging.LogUtils.getLogger().info("BLACK_FOX_NEW_SKILLS_PASS: warned cross cuts, two native sword fans, incidental melee counters, protection and cleanup");
    }
    private static void clock(BlackFoxCombatant actor,int ticks) {
        ((net.minecraft.world.level.storage.ServerLevelData) actor.body().getServer().overworld().getLevelData())
                .setGameTime(actor.body().level().getGameTime()+ticks);
    }
    private static void check(boolean value,String message) { if (!value) throw new IllegalStateException(message); }
    private BlackFoxNewSkillsValidation() { }
}
