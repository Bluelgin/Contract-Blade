package com.maidweapon.forge.compat.fox;

import com.maidweapon.forge.system.fox.challenge.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

/** Real phase-two execution: held native swords, committed portals, damage, counters and cleanup. */
final class BlackFoxDomainValidation {
    static void run(BlackFoxCombatant actor, ServerPlayer player) {
        var boss = actor.body(); var combat = actor.combat(); var fight = combat.fight(); var ranged = combat.ranged();
        var level = (ServerLevel) boss.level();
        ranged.clear(); fight.start(BlackFoxFight.Skill.SWORD_WHEEL);
        player.setPos(Vec3.atBottomCenterOf(actor.arenaOrigin()).add(0, 1, -10));
        for (int age = 1; age <= 33; age++) tick(actor);
        require((ranged.formation().mask() & 4095) == 4095 && (ranged.formation().mask() & BlackFoxSwordFormation.WHEEL_FLAG) != 0,
                "twelve real swords remain held through rotation and stop");
        Vec3 target = player.getEyePosition(); float yaw = ranged.formation().yaw();
        var stopped = BlackFoxSwordFormation.wheelOffset(0, yaw, 32);
        require(stopped.distanceTo(BlackFoxSwordFormation.wheelOffset(0, yaw, 39)) < .0001, "wheel keeps rotating after stop");
        player.setPos(player.position().add(12, 0, 7));
        while (fight.age() < 41) tick(actor);
        require(Integer.bitCount(ranged.formation().mask() & 4095) == 6, "first six-sword release");
        var first = level.getEntitiesOfClass(Projectile.class, boss.getBoundingBox().inflate(32),
                shot -> ranged.owns(shot) && !ranged.held(shot)).get(0);
        require(first.getDeltaMovement().normalize().dot(target.subtract(first.position()).normalize()) > .999, "wheel retargets after warning");
        while (fight.age() < 57) tick(actor);
        require(ranged.formation().mask() == 0 && ranged.activeShots() == 12, "second six-sword release");
        player.invulnerableTime = 0; float health = player.getHealth();
        com.maidweapon.forge.event.BlackFoxCombatEvents.phantomImpact(new net.minecraftforge.event.entity.ProjectileImpactEvent(
                first, new net.minecraft.world.phys.EntityHitResult(player)));
        require(player.getHealth() < health, "wheel native sword cannot damage challenger");
        while (fight.age() < 62) tick(actor);
        require(BlackFoxOpenings.active(fight.skill(), fight.age()) && boss.getY() < actor.arenaOrigin().getY() + 2,
                "wheel recovery does not provide a reachable melee opening");
        ranged.clear(); fight.start(BlackFoxFight.Skill.DOMAIN_CROSS);
        player.setPos(Vec3.atBottomCenterOf(actor.arenaOrigin()).add(0, 1, -8));
        tick(actor); var cross = combat.corruption().cross(); Vec3 locked = cross.target(), left = cross.left(), right = cross.right();
        player.setPos(player.position().add(8, 0, 8));
        while (fight.age() < 31) tick(actor);
        require(ranged.activeShots() == 0 && left.distanceTo(right) > 8 && cross.target().equals(locked), "cross warning or committed geometry");
        tick(actor); require(ranged.activeShots() == 1, "first cross cut at tick 32");
        var cut = level.getEntitiesOfClass(Projectile.class, boss.getBoundingBox().inflate(40), ranged::owns).get(0);
        require(cut.getDeltaMovement().normalize().dot(locked.subtract(cut.position()).normalize()) > .999, "cross follows moving player");
        Vec3 toward = cut.position().subtract(player.position());
        player.setYRot((float) Math.toDegrees(Math.atan2(-toward.x, toward.z)));
        BlackFoxEncounters.swing(player, player.getMainHandItem(), true);
        com.maidweapon.forge.event.BlackFoxCombatEvents.phantomImpact(new net.minecraftforge.event.entity.ProjectileImpactEvent(
                cut, new net.minecraft.world.phys.EntityHitResult(player)));
        require(fight.skill() == BlackFoxFight.Skill.STAGGER && ranged.activeShots() == 0 && combat.protects(player),
                "facing incoming side cut cannot counter and cancel the second cut");
        for (int i = 0; i < 20; i++) tick(actor);
        require(ranged.activeShots() == 0, "cancelled second portal still fires");
        ranged.clear(); fight.start(BlackFoxFight.Skill.DOMAIN_SEAL);
        for (int age = 1; age <= 80; age++) {
            tick(actor);
            require(ranged.activeShots() <= BlackFoxDomainAttacks.MAX_SHOTS, "ambient exceeds projectile cap");
        }
        require(ranged.activeShots() >= 15, "seal no longer summons frequent native sword groups");
        while (fight.age() < 84) tick(actor);
        require(ranged.formation().mask() == 0, "ambient held swords keep firing into recovery");
        ranged.clear(); fight.start(BlackFoxFight.Skill.DOMAIN);
        com.mojang.logging.LogUtils.getLogger().info("BLACK_FOX_DOMAIN_SKILLS_PASS: 12 native swords, stop/lock, two six-sword releases, real damage, fixed cross cuts, directional counter and cleanup");
    }
    private static void tick(BlackFoxCombatant actor) {
        ((net.minecraft.world.level.storage.ServerLevelData) actor.body().getServer().overworld().getLevelData())
                .setGameTime(actor.body().level().getGameTime() + 1);
        actor.combat().tick();
    }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
    private BlackFoxDomainValidation() { }
}
