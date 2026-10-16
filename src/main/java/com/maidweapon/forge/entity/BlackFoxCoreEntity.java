package com.maidweapon.forge.entity;

import com.maidweapon.forge.system.fox.challenge.BlackFoxCombatant;
import com.maidweapon.forge.system.fox.challenge.BlackFoxEncounters;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

/** Targetable phantom-sword/air-melee objective. It has no AI, loot, inventory or saved state. */
public final class BlackFoxCoreEntity extends Monster {
    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> SEAL_SLOT =
            net.minecraft.network.syncher.SynchedEntityData.defineId(BlackFoxCoreEntity.class, net.minecraft.network.syncher.EntityDataSerializers.INT);
    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> OWNER_ID =
            net.minecraft.network.syncher.SynchedEntityData.defineId(BlackFoxCoreEntity.class, net.minecraft.network.syncher.EntityDataSerializers.INT);
    private BlackFoxCombatant encounter;
    @Override protected void defineSynchedData() {
        super.defineSynchedData(); entityData.define(SEAL_SLOT, -1); entityData.define(OWNER_ID, -1);
    }
    public int sealSlot() { return entityData.get(SEAL_SLOT); }
    public int ownerId() { return entityData.get(OWNER_ID); }
    public boolean isSeal() { return sealSlot() >= 0; }
    public void initializeSeal(BlackFoxCombatant owner, int slot) {
        initialize(owner); entityData.set(SEAL_SLOT, slot); entityData.set(OWNER_ID, owner.body().getId());
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(24); setHealth(24);
        setCustomName(net.minecraft.network.chat.Component.translatable("maid_weapon.fox.boss.seal_core"));
    }
    public BlackFoxCoreEntity(EntityType<BlackFoxCoreEntity> type, Level level) {
        super(type, level); setNoGravity(true); setNoAi(true); setGlowingTag(true);
    }
    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 60)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1);
    }
    public void initialize(BlackFoxCombatant owner) { encounter = owner; setHealth(getMaxHealth()); }
    @Override protected void registerGoals() { }
    @Override public boolean isPickable() { return isAlive(); }
    @Override public boolean hurt(DamageSource source, float damage) {
        if (level().isClientSide || encounter == null || !BlackFoxEncounters.authorized(source, encounter)) return false;
        return super.hurt(source, damage);
    }
    @Override public void die(DamageSource source) {
        if (encounter != null) {
            if (isSeal()) encounter.combat().seals().breakCore(this, sealSlot());
            else encounter.combat().corruption().breakCore(this);
        }
        discard();
    }
    @Override public void tick() {
        super.tick();
        if (!level().isClientSide && (encounter == null || !BlackFoxEncounters.registered(encounter))) discard();
    }
    @Override protected boolean shouldDespawnInPeaceful() { return false; }
    @Override public boolean removeWhenFarAway(double distance) { return false; }
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }
}
