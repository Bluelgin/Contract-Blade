package com.maidweapon.forge.compat.fox;

import com.maidweapon.forge.system.fox.challenge.*;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;

/** Actual hurt events, including forceHit-style i-frame resets and the delayed reactive counter. */
final class BlackFoxDefenseValidation {
    static void run(BlackFoxCombatant actor, ServerPlayer player) {
        var boss = actor.body(); var fight = actor.combat().fight();
        boss.setHealth(boss.getMaxHealth());
        player.setPos(boss.position().add(0, 0, -4)); player.setYRot(0); boss.setYRot(180);
        clock(actor, 30); fight.start(BlackFoxFight.Skill.CROSS_CUT);
        boss.invulnerableTime = 0;
        check(boss.hurt(boss.damageSources().playerAttack(player), 10), "ordinary first hit is accepted outside defense window");
        float health = boss.getHealth(); boss.invulnerableTime = 0;
        check(!boss.hurt(boss.damageSources().playerAttack(player), 100) && boss.getHealth() == health,
                "resetting vanilla i-frames cannot bypass the boss-local damage interval");
        clock(actor, 15); boss.invulnerableTime = 0;
        check(!boss.hurt(boss.damageSources().playerAttack(player), 100), "normal interval still blocks at tick fifteen");
        clock(actor, 1); boss.invulnerableTime = 0;
        check(boss.hurt(boss.damageSources().playerAttack(player), 10), "normal interval releases at sixteen ticks");
        clock(actor, 16); fight.start(BlackFoxFight.Skill.STAGGER); boss.invulnerableTime = 0;
        check(boss.hurt(boss.damageSources().playerAttack(player), 10), "stagger keeps a real damage opening");
        clock(actor, 8); boss.invulnerableTime = 0;
        check(boss.hurt(boss.damageSources().playerAttack(player), 10), "stagger permits a fresh hit after eight ticks");
        clock(actor, 30); fight.start(BlackFoxFight.Skill.APPROACH); boss.invulnerableTime = 0;
        health = boss.getHealth();
        check(!boss.hurt(boss.damageSources().playerAttack(player), 20) && boss.getHealth() == health
                        && fight.skill() == BlackFoxFight.Skill.COUNTER_CUT,
                "ready boss meets a front melee attack with its own counter instead of taking damage");
        player.setHealth(1000); player.invulnerableTime = 0;
        for (int age = 1; age <= 10; age++) {
            clock(actor, 1);
            if (age == 10) BlackFoxEncounters.swing(player, player.getMainHandItem(), true);
            actor.combat().tick();
            if (age < 10) check(player.getHealth() == 1000, "reactive parry never deals an immediate hidden hit");
        }
        check(player.getHealth() == 1000 && actor.combat().protects(player), "player can parry the delayed answering cut");
        clock(actor, 30); fight.start(BlackFoxFight.Skill.APPROACH); boss.invulnerableTime = 0;
        check(boss.hurt(boss.damageSources().playerAttack(player), 10), "counter cooldown prevents permanent front invulnerability");
        clock(actor, 30); fight.start(BlackFoxFight.Skill.STAGGER);
        boss.setHealth(boss.getMaxHealth()); player.setHealth(1000);
        LogUtils.getLogger().info("BLACK_FOX_DEFENSE_PASS: sixteen/eight-tick cadence, forceHit resistance, readable parry and counter cooldown");
    }
    private static void clock(BlackFoxCombatant actor, int ticks) {
        ((net.minecraft.world.level.storage.ServerLevelData) actor.body().getServer().overworld().getLevelData())
                .setGameTime(actor.body().level().getGameTime() + ticks);
    }
    private static void check(boolean okay, String detail) { if (!okay) throw new IllegalStateException(detail); }
    private BlackFoxDefenseValidation() { }
}
