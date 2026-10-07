package com.maidweapon.forge.compat.fox;

import com.maidweapon.forge.system.fox.challenge.*;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;

/** Actual committed cuts and completion, not just the pure momentum counter. */
final class BlackFoxSwordplayValidation {
    static void run(BlackFoxCombatant actor, ServerPlayer player) {
        var boss = actor.body(); var fight = actor.combat().fight();
        player.setPos(boss.position().add(0, 0, -4)); player.setYRot(0); boss.setYRot(180);
        reset(actor);
        for (int exchange = 0; exchange < 3; exchange++) {
            probe(actor, player);
            check(exchange < 2 ? fight.skill() == BlackFoxFight.Skill.PROBE_CUT : fight.skill() == BlackFoxFight.Skill.STAGGER,
                    "three different melee windows create a real stagger, not per-projectile accumulation");
        }
        reset(actor);
        probe(actor, player);
        var position = boss.position();
        for (int i = 0; i < 16; i++) tick(actor);
        check(boss.position().equals(position) && (fight.skill() == BlackFoxFight.Skill.CROSS_CUT
                        || fight.skill() == BlackFoxFight.Skill.RETURN_BLADE || fight.skill() == BlackFoxFight.Skill.APPROACH),
                "parried probe answers with a stationary exchange or hold, never an automatic blink");
        reset(actor);
        probe(actor, player); probe(actor, player);
        clock(actor, 30); actor.combat().nativeCombo().stop(); fight.start(BlackFoxFight.Skill.RETURN_BLADE);
        for (int i = 0; i < 7; i++) fight.advance();
        BlackFoxEncounters.swing(player, player.getMainHandItem(), true);
        boss.invulnerableTime = 0;
        boss.hurt(boss.damageSources().playerAttack(player), 20);
        check(fight.skill() == BlackFoxFight.Skill.RETURN_BLADE, "ordinary parry cannot interrupt native B even when momentum breaks");
        for (int i = 0; i < 50 && fight.skill() == BlackFoxFight.Skill.RETURN_BLADE; i++) tick(actor);
        check(fight.skill() == BlackFoxFight.Skill.STAGGER && fight.pressure() > 0,
                "deferred momentum break opens recovery only after the unanswered native B finishes");
        reset(actor); player.setHealth(1000);
        LogUtils.getLogger().info("BLACK_FOX_SWORDPLAY_PASS: distinct melee momentum, probe response and deferred native B break");
    }
    private static void probe(BlackFoxCombatant actor, ServerPlayer player) {
        clock(actor, 30); actor.combat().fight().start(BlackFoxFight.Skill.PROBE_CUT);
        for (int i = 1; i <= 12; i++) {
            if (i == 12) BlackFoxEncounters.swing(player, player.getMainHandItem(), true);
            tick(actor);
        }
    }
    private static void reset(BlackFoxCombatant actor) {
        clock(actor, 30); actor.combat().nativeCombo().stop();
        actor.combat().fight().start(BlackFoxFight.Skill.STAGGER); actor.combat().tick();
    }
    private static void tick(BlackFoxCombatant actor) { clock(actor, 1); actor.combat().tick(); }
    private static void clock(BlackFoxCombatant actor, int ticks) {
        ((net.minecraft.world.level.storage.ServerLevelData) actor.body().getServer().overworld().getLevelData())
                .setGameTime(actor.body().level().getGameTime() + ticks);
    }
    private static void check(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
    private BlackFoxSwordplayValidation() { }
}
