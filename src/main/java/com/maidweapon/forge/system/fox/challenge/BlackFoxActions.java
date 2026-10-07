package com.maidweapon.forge.system.fox.challenge;

import com.maidweapon.forge.compat.BlackFoxSlashCompat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/** Shared movement, geometry and hit effects. Contains no encounter lifecycle or skill selection. */
final class BlackFoxActions {
    private final BlackFoxCombatant actor;
    final BlackFoxFight fight;
    private final com.maidweapon.forge.compat.BlackFoxNativeCombo nativeCombo;
    private final BlackFoxDomainAttacks ranged;
    Vec3 committed = Vec3.ZERO;
    Vec3 laneStart = Vec3.ZERO, laneEnd = Vec3.ZERO;
    BlackFoxActions(BlackFoxCombatant actor, BlackFoxFight fight,
                    com.maidweapon.forge.compat.BlackFoxNativeCombo nativeCombo, BlackFoxDomainAttacks ranged) {
        this.actor = actor; this.fight = fight; this.nativeCombo = nativeCombo; this.ranged = ranged;
    }
    Mob boss() { return actor.body(); }
    com.maidweapon.forge.compat.BlackFoxNativeCombo nativeCombo() { return nativeCombo; }
    BlackFoxDomainAttacks ranged() { return ranged; }
    void begin(BlackFoxFight.Skill skill, ServerPlayer player) {
        fight.start(skill); commit(player);
    }
    void commit(ServerPlayer player) {
        committed = horizontal(player.position().subtract(boss().position())).normalize();
        if (committed.lengthSqr() < .01) committed = horizontal(boss().getLookAngle()).normalize();
        face(boss().position().add(committed));
    }
    void slash(ServerPlayer player, float damage, double reach, float roll) {
        strike(player, damage, reach, roll, .25);
    }
    void cut(ServerPlayer player, float damage, double reach, float roll) {
        face(boss().position().add(committed));
        strike(player, damage, BlackFoxSlashPresentation.melee(fight.skill()) ? BlackFoxSlashPresentation.MELEE_REACH : reach,
                roll,BlackFoxSlashPresentation.MELEE_DOT);
    }
    private void strike(ServerPlayer player, float damage, double reach, float roll, double arc) {
        markSlash();
        if(!BlackFoxSlashPresentation.melee(fight.skill())) BlackFoxSlashCompat.slash(boss(), roll);
        boss().playSound(net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_SWEEP, .8f, .8f);
        if (boss().distanceToSqr(player) > reach * reach || horizontal(boss().getLookAngle()).normalize()
                .dot(horizontal(player.position().subtract(boss().position())).normalize()) < arc) return;
        if (actor.combat().protects(player)) return;
        if (parry(player)) return;
        player.hurt(boss().damageSources().mobAttack(boss()), fight.phaseTwo() ? damage * 1.15f : damage);
    }
    void markSlash() { actor.markSlash(fight.skill(),fight.age()); }
    boolean parry(ServerPlayer player) {
        return parry(player,false);
    }
    boolean parryArena(ServerPlayer player) { return parry(player,true); }
    private boolean parry(ServerPlayer player,boolean arena) {
        if ((!arena && (!front(player) || !facingBoss(player))) || !BlackFoxEncounters.consumeParry(player)) return false;
        actor.combat().protectParry();
        actor.combat().feedbackParry(player, false);
        BlackFoxEffects.contact(boss(), player, false);
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable("maid_weapon.fox.boss.parry"), true);
        return true;
    }
    boolean protectedPlayer(ServerPlayer player) { return actor.combat().protects(player); }
    boolean front(net.minecraft.world.entity.Entity attacker) { return front(attacker.position()); }
    boolean front(Vec3 at) {
        return horizontal(boss().getLookAngle()).normalize().dot(horizontal(at.subtract(boss().position())).normalize()) >= .25;
    }
    boolean facingBoss(ServerPlayer player) {
        return horizontal(player.getLookAngle()).normalize().dot(horizontal(boss().position().subtract(player.position())).normalize()) >= .25;
    }
    void face(Vec3 point) {
        Vec3 diff = point.subtract(boss().position());
        float yaw = (float) Math.toDegrees(Math.atan2(-diff.x, diff.z));
        boss().setYRot(yaw); boss().setYHeadRot(yaw); boss().setYBodyRot(yaw); boss().setXRot(0);
    }
    void move(Vec3 movement) { boss().setDeltaMovement(movement.x, boss().getDeltaMovement().y, movement.z); }
    void stopHorizontal() { move(Vec3.ZERO); }
    static Vec3 horizontal(Vec3 vector) { return new Vec3(vector.x, 0, vector.z); }
    Vec3 bounded(Vec3 vector) {
        var origin = actor.arenaOrigin();
        return new Vec3(net.minecraft.util.Mth.clamp(vector.x, origin.getX() - 20, origin.getX() + 20),
                origin.getY() + 1, net.minecraft.util.Mth.clamp(vector.z, origin.getZ() - 20, origin.getZ() + 20));
    }
    void clamp() {
        Vec3 at = boss().position(), safe = bounded(at);
        if (Math.abs(at.x - safe.x) > .01 || Math.abs(at.z - safe.z) > .01 || at.y < actor.arenaOrigin().getY())
            boss().setPos(safe.x, Math.max(safe.y, at.y), safe.z);
        if (boss().getY() > actor.arenaOrigin().getY() + 13) boss().setDeltaMovement(boss().getDeltaMovement().multiply(1, 0, 1).add(0, -.35, 0));
    }
    double distanceToLane(Vec3 point) {
        Vec3 span = horizontal(laneEnd.subtract(laneStart)), offset = horizontal(point.subtract(laneStart));
        double ratio = Math.max(0, Math.min(1, offset.dot(span) / Math.max(.001, span.lengthSqr())));
        return offset.subtract(span.scale(ratio)).length();
    }
}

