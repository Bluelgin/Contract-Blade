package com.maidweapon.forge.compat.fox;

import com.maidweapon.forge.system.fox.challenge.*;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;

/** Actual hurt events: native immunity, no added damage timer, and unchanged reactive counter. */
final class BlackFoxDefenseValidation {
    static void run(BlackFoxCombatant actor, ServerPlayer player) throws Exception {
        var boss = actor.body(); var fight = actor.combat().fight();
        player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000);
        player.setHealth(1000);
        boss.setHealth(boss.getMaxHealth());
        player.setPos(boss.position().add(0, 0, -4)); player.setYRot(0); boss.setYRot(180);
        clock(actor, 30); fight.start(BlackFoxFight.Skill.CROSS_CUT);
        boss.invulnerableTime = 0;
        check(boss.hurt(boss.damageSources().playerAttack(player), 10), "ordinary first hit is accepted outside defense window");
        float health = boss.getHealth();
        check(boss.invulnerableTime > 0, "accepted hit retains native immunity rather than clearing it");
        check(!boss.hurt(boss.damageSources().playerAttack(player), 10) && boss.getHealth() == health,
                "native immunity still rejects repeated equal damage");
        check(boss.hurt(boss.damageSources().playerAttack(player), 20) && boss.getHealth() < health,
                "native stronger-hit difference is not blocked by a custom sixteen-tick interval");
        boss.invulnerableTime = 0;
        check(boss.hurt(boss.damageSources().playerAttack(player), 10),
                "without native immunity no extra normal-hit timer remains in the same tick");
        clock(actor, 16); fight.start(BlackFoxFight.Skill.STAGGER); boss.invulnerableTime = 0;
        check(boss.hurt(boss.damageSources().playerAttack(player), 10), "stagger keeps a real damage opening");
        health = boss.getHealth();
        check(!boss.hurt(boss.damageSources().playerAttack(player), 10) && boss.getHealth() == health,
                "stagger also retains native repeated-hit protection");
        boss.invulnerableTime = 0;
        check(boss.hurt(boss.damageSources().playerAttack(player), 10),
                "without native immunity stagger adds no eight-tick damage timer");
        clock(actor, 30); fight.start(BlackFoxFight.Skill.APPROACH); boss.invulnerableTime = 0;
        health = boss.getHealth();
        BlackFoxGuardValidation.input(player, 30);
        player.getMainHandItem().enchant(net.minecraft.world.item.enchantment.Enchantments.THORNS, 1);
        check(!boss.hurt(boss.damageSources().playerAttack(player), 20) && boss.getHealth() == health
                        && fight.skill() == BlackFoxFight.Skill.COUNTER_CUT,
                "ready boss meets a front melee attack with its own counter instead of taking damage");
        player.setHealth(1000); player.invulnerableTime = 0;
        for (int age = 1; age <= 10; age++) {
            clock(actor, 1);
            if (age == 10) BlackFoxGuardValidation.input(player, 0, "SNEAK");
            actor.combat().tick();
            if (age < 10) check(player.getHealth() == 1000, "reactive parry never deals an immediate hidden hit");
        }
        check(player.getHealth() == 1000 && actor.combat().protects(player), "player can parry the delayed answering cut");
        BlackFoxGuardValidation.input(player, 30);
        clock(actor, 30); fight.start(BlackFoxFight.Skill.APPROACH); boss.invulnerableTime = 0;
        check(boss.hurt(boss.damageSources().playerAttack(player), 10), "counter cooldown prevents permanent front invulnerability");
        clock(actor, 30); fight.start(BlackFoxFight.Skill.STAGGER);
        boss.setHealth(boss.getMaxHealth()); player.setHealth(1000);
        LogUtils.getLogger().info("BLACK_FOX_DEFENSE_PASS: native hit immunity, no added damage timer, readable parry and counter cooldown");
    }
    private static void clock(BlackFoxCombatant actor, int ticks) {
        ((net.minecraft.world.level.storage.ServerLevelData) actor.body().getServer().overworld().getLevelData())
                .setGameTime(actor.body().level().getGameTime() + ticks);
    }
    private static void check(boolean okay, String detail) { if (!okay) throw new IllegalStateException(detail); }
    private BlackFoxDefenseValidation() { }
}
