package com.maidweapon.forge.system.fox.challenge;

import com.maidweapon.forge.compat.BlackFoxJudgementCompat;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/** Owns only the finite ground rifts and their native visual entities, not phase transitions. */
final class BlackFoxRifts {
    private record Rift(Vec3 at, long born) { }
    private final BlackFoxCombatant actor;
    private final BlackFoxActions actions;
    private final List<Rift> rifts = new ArrayList<>();
    private final List<Entity> visuals = new ArrayList<>();
    BlackFoxRifts(BlackFoxCombatant actor, BlackFoxActions actions) { this.actor = actor; this.actions = actions; }
    private long now() { return actor.body().level().getGameTime(); }
    void pruneVisuals() { visuals.removeIf(Entity::isRemoved); }
    void spawn(ServerPlayer player, Vec3 center) {
        rifts.removeIf(rift -> now() - rift.born() >= 100);
        if (rifts.size() >= 6) return;
        Vec3 at = actions.bounded(player.position());
        rifts.add(new Rift(at.add(0, 1, 0), now()));
        double angle = actor.body().getRandom().nextDouble() * Math.PI * 2;
        rifts.add(new Rift(center.add(Math.cos(angle) * 13, 1, Math.sin(angle) * 13), now()));
    }
    void tick(ServerPlayer player) {
        var level = (ServerLevel) actor.body().level();
        rifts.removeIf(rift -> now() - rift.born() >= 100);
        for (var rift : rifts) {
            long age = now() - rift.born();
            if (age < 20) {
                if (age % 4 == 0) level.sendParticles(ParticleTypes.WITCH, rift.at().x, rift.at().y - .8,
                        rift.at().z, 8, .8, .05, .8, 0);
            } else {
                if (age % 8 == 0) {
                    var visual = BlackFoxJudgementCompat.visual(level, rift.at());
                    if (level.addFreshEntity(visual)) visuals.add(visual);
                }
                if (age % 10 == 0 && player.position().add(0, 1, 0).distanceToSqr(rift.at()) < 2.5 * 2.5
                        && !actor.combat().protects(player))
                    player.hurt(actor.body().damageSources().mobAttack(actor.body()), 7);
            }
        }
    }
    void clear() { rifts.clear(); visuals.forEach(Entity::discard); visuals.clear(); }
}
