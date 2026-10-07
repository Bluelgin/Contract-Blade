package com.maidweapon.forge.compat.fox;

import com.maidweapon.forge.system.fox.challenge.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

/** Native hold/release, teleport, fixed aim and cleanup regression in the opt-in fixture world. */
final class BlackFoxSwordFormationValidation {
    static void run(BlackFoxCombatant actor, ServerPlayer player) {
        var ranged = actor.combat().ranged();
        ranged.clear();
        actor.combat().fight().start(BlackFoxFight.Skill.PHANTOM_FEINT);
        var boss = actor.body();
        boss.setPos(Vec3.atBottomCenterOf(actor.arenaOrigin()).add(0,1,0));
        player.setPos(boss.position().add(0,0,-8));
        ranged.volley(player);
        check(ranged.activeShots() == 3 && ranged.formation().mask() == 7, "three native swords held");
        var level = (net.minecraft.server.level.ServerLevel) boss.level();
        var shots = level.getEntitiesOfClass(Projectile.class, boss.getBoundingBox().inflate(16), ranged::held);
        check(shots.size() == 3, "actual native sword entities, not visual placeholders");
        for (var shot : shots) {
            var before = shot.position(); shot.tick();
            check(shot.position().equals(before) && shot.getDeltaMovement().lengthSqr() == 0, "native held tick does not drift or hit");
            var impact = new net.minecraftforge.event.entity.ProjectileImpactEvent(shot,
                    new net.minecraft.world.phys.EntityHitResult(player));
            com.maidweapon.forge.event.BlackFoxCombatEvents.phantomImpact(impact);
            check(impact.getImpactResult() == net.minecraftforge.event.entity.ProjectileImpactEvent.ImpactResult.SKIP_ENTITY,
                    "held swords have no offensive impact");
        }
        boss.setPos(boss.position().add(3,0,0));
        ranged.tick(player, false);
        for (var shot : shots) check(shot.position().distanceTo(boss.position()) < 3, "held formation follows teleport");
        clock(actor, BlackFoxSwordFormation.FIRST_HOLD - BlackFoxSwordFormation.LOCK_LEAD);
        ranged.tick(player, false);
        var target = player.getEyePosition();
        player.setPos(player.position().add(8,0,0));
        clock(actor, BlackFoxSwordFormation.LOCK_LEAD);
        ranged.tick(player, false);
        check(Integer.bitCount(ranged.formation().mask()) == 2, "first sword releases after warning, not all at once");
        var first = shots.stream().filter(shot -> !ranged.held(shot)).findFirst().orElseThrow();
        check(Math.abs(first.getDeltaMovement().length()-1.2) < .001, "fast phase-one launch speed");
        check(first.getDeltaMovement().normalize().dot(target.subtract(first.position()).normalize()) > .999,
                "launch keeps locked aim after player moves");
        var flight = first.getDeltaMovement();
        boss.setPos(boss.position().add(2,0,0));
        clock(actor, 3); ranged.tick(player, false);
        check(first.getDeltaMovement().equals(flight) && Integer.bitCount(ranged.formation().mask()) == 1,
                "teleport does not redirect released sword; next sword follows staggered cadence");
        clock(actor, 3); ranged.tick(player, false);
        check(ranged.formation().mask() == 0, "all three released on schedule");
        ranged.volley(player);
        check(ranged.formation().mask() != 0, "new formation can start");
        actor.combat().fight().start(BlackFoxFight.Skill.STAGGER);
        ranged.tick(player, false);
        check(ranged.formation().mask() == 0, "stagger cancels held swords");
        ranged.clear();
        check(ranged.activeShots() == 0, "session cleanup removes every held/flying sword");
        com.mojang.logging.LogUtils.getLogger().info("BLACK_FOX_SWORD_FORMATION_PASS: native hold, teleport, warning, locked aim, staggered release and cleanup");
    }
    private static void clock(BlackFoxCombatant actor, int ticks) {
        ((net.minecraft.world.level.storage.ServerLevelData) actor.body().getServer().overworld().getLevelData())
                .setGameTime(actor.body().level().getGameTime() + ticks);
    }
    static void secondPhase(BlackFoxCombatant actor, ServerPlayer player) {
        var ranged = actor.combat().ranged(); ranged.clear(); ranged.volley(player);
        check(ranged.formation().mask() == 31, "phase two holds five swords");
        clock(actor, BlackFoxSwordFormation.SECOND_HOLD - 1); ranged.tick(player, false);
        check(ranged.formation().mask() == 31, "phase-two hold lasts ten ticks");
        clock(actor, 1); ranged.tick(player, false);
        check(Integer.bitCount(ranged.formation().mask()) == 4, "phase two first release");
        for (int left = 3; left >= 0; left--) {
            clock(actor, 2); ranged.tick(player, false);
            check(Integer.bitCount(ranged.formation().mask()) == left, "phase-two two-tick cadence");
        }
        var shots = ((net.minecraft.server.level.ServerLevel) actor.body().level()).getEntitiesOfClass(
                Projectile.class, actor.body().getBoundingBox().inflate(16), ranged::owns);
        check(shots.size() == 5 && shots.stream().allMatch(shot -> Math.abs(shot.getDeltaMovement().length()-1.5) < .001),
                "five native phase-two swords launch at the faster speed");
        com.mojang.logging.LogUtils.getLogger().info("BLACK_FOX_SECOND_FORMATION_PASS: five swords, ten-tick hold, two-tick cadence and native launch speed");
    }
    private static void check(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
    private BlackFoxSwordFormationValidation() { }
}
