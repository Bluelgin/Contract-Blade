package com.maidweapon.forge.entity;

import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;

/** Actual traveling shot, with native sword/drive visuals and strictly private server damage selection. */
public final class BlackFoxSwordBoltEntity extends Entity {
    private static final EntityDataAccessor<Boolean> BLADE = SynchedEntityData.defineId(BlackFoxSwordBoltEntity.class, EntityDataSerializers.BOOLEAN);
    private UUID owner, target;
    private float damage, speed;
    private Vec3 trackingCentre;
    public void trackWithin(Vec3 centre) { trackingCentre = centre; }
    public BlackFoxSwordBoltEntity(EntityType<BlackFoxSwordBoltEntity> type, net.minecraft.world.level.Level level) { super(type, level); noPhysics = true; setNoGravity(true); }
    @Override protected void defineSynchedData() { entityData.define(BLADE, false); }
    public boolean blade() { return entityData.get(BLADE); }
    public void initialize(ServerPlayer player, LivingEntity enemy, Vec3 at, boolean blade, float amount, float velocity) {
        owner = player.getUUID(); target = enemy.getUUID(); damage = amount; speed = velocity;
        entityData.set(BLADE, blade); setPos(at); setDeltaMovement(enemy.getEyePosition().subtract(at).normalize().scale(speed));
        rotate();
    }
    private void rotate() {
        var direction = getDeltaMovement();
        setYRot((float)Math.toDegrees(Math.atan2(direction.x, direction.z)));
        setXRot((float)Math.toDegrees(Math.atan2(direction.y, direction.horizontalDistance())));
    }
    @Override public void tick() {
        super.tick(); if (level().isClientSide) return;
        var level = (net.minecraft.server.level.ServerLevel)level();
        ServerPlayer player = owner == null ? null : level.getServer().getPlayerList().getPlayer(owner);
        var entity = target == null ? null : level.getEntity(target);
        if (tickCount > 60 || player == null || !player.isAlive() || player.level() != level
                || !(entity instanceof LivingEntity enemy) || !BlackFoxSwordCoreEntity.hostile(player, enemy)) { discard(); return; }
        Vec3 before = position();
        if (trackingCentre != null) {
            if (enemy.position().distanceToSqr(trackingCentre) > com.maidweapon.forge.system.fox.BlackFoxSwordCoreTimeline.RADIUS * com.maidweapon.forge.system.fox.BlackFoxSwordCoreTimeline.RADIUS) { discard(); return; }
            setDeltaMovement(enemy.getEyePosition().subtract(before).normalize().scale(speed));
        }
        if (!blade() && tickCount <= 8) setDeltaMovement(getDeltaMovement().normalize().lerp(enemy.getEyePosition().subtract(before).normalize(), .12).normalize().scale(speed));
        Vec3 after = before.add(getDeltaMovement()); rotate();
        var block = level.clip(new net.minecraft.world.level.ClipContext(before, after, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, this));
        if (block.getType() != net.minecraft.world.phys.HitResult.Type.MISS) after = block.getLocation();
        var contact = enemy.getBoundingBox().inflate(blade() ? .65 : .25);
        if (contact.contains(before) || contact.clip(before, after).isPresent()) {
            enemy.hurt(damageSources().playerAttack(player), damage);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.SWEEP_ATTACK, enemy.getX(), enemy.getY() + 1, enemy.getZ(), 1, 0, 0, 0, 0);
            discard(); return;
        }
        if (block.getType() != net.minecraft.world.phys.HitResult.Type.MISS) { discard(); return; }
        setPos(after);
    }
    @Override protected void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) { }
    @Override protected void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) { }
    @Override public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getAddEntityPacket() { return net.minecraftforge.network.NetworkHooks.getEntitySpawningPacket(this); }
}
