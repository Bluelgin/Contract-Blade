package com.maidweapon.forge.entity;

import com.maidweapon.forge.system.akatsuki.soul.SoulAnchorService;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

/** An attackable, stationary soul fragment. No mob AI, loot, target health, or persistence. */
public final class SoulAnchorEntity extends LivingEntity {
  private static final EntityDataAccessor<Integer> TARGET =
      SynchedEntityData.defineId(SoulAnchorEntity.class, EntityDataSerializers.INT);
  private static final EntityDataAccessor<Integer> SHAKE =
      SynchedEntityData.defineId(SoulAnchorEntity.class, EntityDataSerializers.INT);
  private static final EntityDataAccessor<Optional<UUID>> OWNER =
      SynchedEntityData.defineId(SoulAnchorEntity.class, EntityDataSerializers.OPTIONAL_UUID);
  public UUID owner, original;

  public void owner(UUID id) {
    owner = id;
    entityData.set(OWNER, Optional.of(id));
  }

  public Optional<UUID> ownerId() {
    return entityData.get(OWNER);
  }

  public String binding = "";
  public long expires;

  public SoulAnchorEntity(EntityType<? extends SoulAnchorEntity> type, Level level) {
    super(type, level);
    noPhysics = true;
    setNoGravity(true);
    setHealth(1);
  }

  protected void defineSynchedData() {
    super.defineSynchedData();
    entityData.define(OWNER, Optional.empty());
    entityData.define(TARGET, -1);
    entityData.define(SHAKE, -100);
  }

  public void targetId(int id) {
    entityData.set(TARGET, id);
  }

  public int targetId() {
    return entityData.get(TARGET);
  }

  public void shake() {
    entityData.set(SHAKE, tickCount);
  }

  public float shakeAmount(float partial) {
    return Math.min(1, Math.max(0, 1 - (tickCount + partial - entityData.get(SHAKE)) / 5f));
  }

  @Override
  public void tick() {
    // Deliberately no LivingEntity.tick: no potion, movement, fall, collision or death side
    // effects.
    tickCount++;
    xo = getX();
    yo = getY();
    zo = getZ();
    if (!level().isClientSide && !SoulAnchorService.valid(this)) discard();
  }

  @Override
  public boolean hurt(DamageSource source, float amount) {
    return !level().isClientSide && amount > 0 && SoulAnchorService.strike(this, source);
  }

  @Override
  public boolean isPickable() {
    if (isRemoved()) return false;
    // Other clients aim through foreign anchors instead of having their normal melee blocked.
    if (!level().isClientSide) return true;
    return Boolean.TRUE.equals(
        net.minecraftforge.fml.DistExecutor.unsafeCallWhenOn(
            net.minecraftforge.api.distmarker.Dist.CLIENT,
            () -> () -> com.maidweapon.forge.client.SoulAnchorRenderer.owned(this)));
  }

  @Override
  public boolean isPushable() {
    return false;
  }

  @Override
  public void push(Entity other) {}

  @Override
  public void die(DamageSource source) {
    discard();
  }

  @Override
  public Iterable<ItemStack> getArmorSlots() {
    return List.of();
  }

  @Override
  public ItemStack getItemBySlot(EquipmentSlot slot) {
    return ItemStack.EMPTY;
  }

  @Override
  public void setItemSlot(EquipmentSlot slot, ItemStack stack) {}

  @Override
  public HumanoidArm getMainArm() {
    return HumanoidArm.RIGHT;
  }

  @Override
  public boolean shouldBeSaved() {
    return false;
  }

  @Override
  public void addAdditionalSaveData(CompoundTag tag) {}

  @Override
  public void readAdditionalSaveData(CompoundTag tag) {
    discard();
  }

  @Override
  public Packet<ClientGamePacketListener> getAddEntityPacket() {
    return NetworkHooks.getEntitySpawningPacket(this);
  }
}
