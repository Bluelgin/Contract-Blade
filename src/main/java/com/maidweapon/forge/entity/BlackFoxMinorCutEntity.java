package com.maidweapon.forge.entity;

import com.maidweapon.forge.system.fox.challenge.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

/** One small, single-impact cut. Weak initial steering, then a dodgeable committed flight. */
public final class BlackFoxMinorCutEntity extends Projectile {
    public BlackFoxMinorCutEntity(EntityType<BlackFoxMinorCutEntity> type, Level level) {
        super(type, level); setNoGravity(true); noPhysics = true;
    }
    @Override protected void defineSynchedData() { }
    @Override public void tick() {
        super.tick();
        if (level().isClientSide) { setPos(position().add(getDeltaMovement())); return; }
        if (!(getOwner() instanceof BlackFoxCombatant boss) || !BlackFoxEncounters.registered(boss)
                || boss.combat().fight().phaseTwo() || tickCount > 60) { discard(); return; }
        var player = getServer().getPlayerList().getPlayer(boss.challenger());
        if (player == null || !player.isAlive() || player.level() != level()) { discard(); return; }
        if (tickCount <= 10) {
            Vec3 aim = player.getEyePosition().subtract(position()).normalize();
            setDeltaMovement(getDeltaMovement().normalize().lerp(aim, .08).normalize().scale(.34));
        }
        Vec3 before = position(), after = before.add(getDeltaMovement());
        if (!FoxChallengeArena.contains(boss.arenaOrigin(), after.x, after.y, after.z)) { discard(); return; }
        setPos(after);
        ((ServerLevel) level()).sendParticles(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY(), getZ(), 1, 0, 0, 0, 0);
        var contact = player.getBoundingBox().inflate(.65);
        if (contact.contains(before) || contact.clip(before, after).isPresent()) {
            if (!boss.combat().protects(player) && !boss.combat().parryNative(player))
                player.hurt(boss.body().damageSources().mobAttack(boss.body()), 8);
            ((ServerLevel) level()).sendParticles(BlackFoxEffects.POLLUTION, getX(), getY(), getZ(), 10, .2, .2, .2, .02);
            playSound(net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_SWEEP, .5f, 1.3f);
            discard();
        }
    }
    @Override public boolean isPickable() { return false; }
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }
}
