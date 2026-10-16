package com.maidweapon.forge.entity;

import com.maidweapon.forge.compat.BlackFoxSwordArtCompat;
import com.maidweapon.forge.system.fox.BlackFoxSwordCoreTimeline;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import java.util.UUID;
import java.util.List;

/** Fixed formation whose native SlashBlade attacks are performed by private living actors. */
public final class BlackFoxSwordCoreEntity extends Entity {
    private static final EntityDataAccessor<Integer> FOLLOW_AT = SynchedEntityData.defineId(BlackFoxSwordCoreEntity.class, EntityDataSerializers.INT);
    private UUID owner, bladeOrigin;
    private Vec3 centre = Vec3.ZERO;
    private float power;
    private final java.util.List<Entity> shots = new java.util.ArrayList<>();
    private final java.util.Map<UUID, BlackFoxSwordActorEntity> hunters = new java.util.HashMap<>();
    private BlackFoxSwordActorEntity emitter;
    public BlackFoxSwordCoreEntity(EntityType<BlackFoxSwordCoreEntity> type, net.minecraft.world.level.Level level) { super(type, level); noPhysics = true; setNoGravity(true); }
    @Override protected void defineSynchedData() { entityData.define(FOLLOW_AT, -1); }
    public boolean following() { return entityData.get(FOLLOW_AT) >= 0; }
    public int followAge() { return following() ? tickCount - entityData.get(FOLLOW_AT) : -1; }
    public void initialize(ServerPlayer player) {
        owner = player.getUUID(); bladeOrigin = player.getMainHandItem().getTag().getUUID(com.maidweapon.forge.system.fox.FoxSpiritState.ORIGIN);
        var locked = BlackFoxSwordArtCompat.lock(player);
        centre = locked instanceof LivingEntity living && hostile(player, living) && player.distanceToSqr(living) <= 24 * 24
                ? living.position() : player.position().add(new Vec3(player.getLookAngle().x, 0, player.getLookAngle().z).normalize().scale(5));
        setPos(centre.add(0, 7, 0));
        power = (float)Math.max(4, Math.min(12, com.maidweapon.forge.compat.WhiteFoxSpecialEffectCompat.strength(player.getMainHandItem()) * .6));
    }
    private boolean hasBlade(ServerPlayer player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            var blade = player.getInventory().getItem(slot);
            if (BlackFoxSwordArtCompat.entitled(blade, player) && blade.getTag().getUUID(com.maidweapon.forge.system.fox.FoxSpiritState.ORIGIN).equals(bladeOrigin)) return true;
        }
        return false;
    }
    public static boolean hostile(ServerPlayer player, LivingEntity living) {
        if (living == player || !living.isAlive() || living instanceof net.minecraft.world.entity.player.Player
                || living instanceof TamableAnimal || living.isAlliedTo(player) || player.isAlliedTo(living)) return false;
        if (net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(living.getType()).getNamespace().equals("touhou_little_maid")) return false;
        if (living instanceof NeutralMob neutral && !neutral.isAngryAt(player)) return false;
        return living instanceof Enemy || living instanceof Mob mob && mob.getTarget() == player;
    }
    public boolean eligible(ServerPlayer player, LivingEntity living) {
        return hostile(player, living) && living.level() == level() && living.position().distanceToSqr(centre) <= BlackFoxSwordCoreTimeline.RADIUS * BlackFoxSwordCoreTimeline.RADIUS
                && level().clip(new net.minecraft.world.level.ClipContext(position(), living.getEyePosition(), net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, this)).getType() == net.minecraft.world.phys.HitResult.Type.MISS;
    }
    private List<LivingEntity> targets(ServerPlayer player) {
        return level().getEntitiesOfClass(LivingEntity.class, new AABB(centre, centre).inflate(BlackFoxSwordCoreTimeline.RADIUS), living -> eligible(player, living)).stream()
                .sorted(java.util.Comparator.comparingDouble(living -> living.distanceToSqr(this))).limit(BlackFoxSwordCoreTimeline.MAX_TARGETS).toList();
    }
    public LivingEntity followTarget(ServerPlayer player) {
        var locked = BlackFoxSwordArtCompat.lock(player);
        if (locked instanceof LivingEntity living && eligible(player, living)) return living;
        var targets = targets(player); return targets.isEmpty() ? null : targets.get(0);
    }
    public void follow(LivingEntity living) {
        clearAttacks();
        entityData.set(FOLLOW_AT, following() ? -1 : tickCount);
    }
    public void capture(Entity shot) {
        shots.removeIf(Entity::isRemoved);
        if (shots.size() >= 96) shot.discard(); else shots.add(shot);
    }
    @Override public void tick() {
        super.tick(); if (level().isClientSide) return;
        shots.removeIf(Entity::isRemoved);
        var level = (net.minecraft.server.level.ServerLevel)level();
        ServerPlayer player = owner == null ? null : level.getServer().getPlayerList().getPlayer(owner);
        if (player == null || !player.isAlive() || player.level() != level || player.distanceToSqr(this) > 64 * 64 || !hasBlade(player)) { discard(); return; }
        if (following()) {
            if (tickCount >= BlackFoxSwordCoreTimeline.DURATION) { discard(); return; }
            var enemies = targets(player);
            hunters.entrySet().removeIf(entry -> {
                boolean remove = entry.getValue().isRemoved() || enemies.stream().noneMatch(enemy -> enemy.getUUID().equals(entry.getKey()));
                if (remove) entry.getValue().discard(); return remove;
            });
            for (var enemy : enemies) if (!hunters.containsKey(enemy.getUUID())) {
                var actor = com.maidweapon.forge.compat.fox.BlackFoxBossCompat.spawnSwordActor(this, player, enemy);
                if (actor != null) hunters.put(enemy.getUUID(), actor);
            }
        } else {
            if (tickCount >= BlackFoxSwordCoreTimeline.DURATION) {
                discard(); return;
            }
            if (tickCount >= BlackFoxSwordCoreTimeline.DEPLOY && tickCount % 12 == 0) {
                var targets = targets(player);
                if (!targets.isEmpty()) for (int shot = 0; shot < 4; shot++) {
                    boolean blade = (shot & 1) != 0;
                    fire(player, targets.get((tickCount / 12 + shot) % targets.size()), blade, power * (blade ? .6f : .35f), tickCount / 12 + shot, blade ? 1.5f : 1.25f);
                }
            }
        }
    }
    private void fire(ServerPlayer player, LivingEntity living, boolean blade, float damage, int angle, float speed) {
        if (shots.size() >= 48) return;
        if (emitter == null || emitter.isRemoved()) emitter = com.maidweapon.forge.compat.fox.BlackFoxBossCompat.spawnSwordActor(this, player, null);
        if (emitter == null) return;
        Vec3 from = position().add(Math.cos(angle * Math.PI / 3) * 2.4, 0, Math.sin(angle * Math.PI / 3) * 2.4);
        var shot = com.maidweapon.forge.compat.BlackFoxPhantomCompat.spawn(emitter, from, living.getEyePosition().subtract(from).normalize(), blade, speed);
        com.maidweapon.forge.compat.BlackFoxPhantomCompat.damage(shot, damage);
    }
    private void clearShots() { shots.forEach(Entity::discard); shots.clear(); }
    private void clearAttacks() {
        clearShots(); hunters.values().forEach(Entity::discard); hunters.clear();
        if (emitter != null) { emitter.discard(); emitter = null; }
    }
    @Override public void remove(RemovalReason reason) { clearAttacks(); super.remove(reason); }
    @Override protected void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) { }
    @Override protected void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) { }
    @Override public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getAddEntityPacket() { return net.minecraftforge.network.NetworkHooks.getEntitySpawningPacket(this); }
}
