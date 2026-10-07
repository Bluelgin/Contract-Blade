package com.maidweapon.forge.entity;

import com.maidweapon.forge.compat.BlackFoxSlashCompat;
import com.maidweapon.forge.system.fox.challenge.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;
import java.util.UUID;

/** Session-owned combatant. No companion inventory, owner, work, healing or contract state. */
public final class BlackFoxBossEntity extends Monster implements BlackFoxCombatant {
    private static final EntityDataAccessor<Integer> MOTION = SynchedEntityData.defineId(BlackFoxBossEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> PHASE = SynchedEntityData.defineId(BlackFoxBossEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Long> MOTION_AT = SynchedEntityData.defineId(BlackFoxBossEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> COMBO_STAGE = SynchedEntityData.defineId(BlackFoxBossEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> COMBO_AT = SynchedEntityData.defineId(BlackFoxBossEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> SKILL = SynchedEntityData.defineId(BlackFoxBossEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> SKILL_AT = SynchedEntityData.defineId(BlackFoxBossEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<org.joml.Vector3f> SINK = SynchedEntityData.defineId(BlackFoxBossEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<org.joml.Vector3f> EMERGE = SynchedEntityData.defineId(BlackFoxBossEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Float> STRIKE_YAW = SynchedEntityData.defineId(BlackFoxBossEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Long> SWORDS_AT = SynchedEntityData.defineId(BlackFoxBossEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> SWORDS_MASK = SynchedEntityData.defineId(BlackFoxBossEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> SWORDS_YAW = SynchedEntityData.defineId(BlackFoxBossEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<org.joml.Vector3f> CROSS_LEFT = SynchedEntityData.defineId(BlackFoxBossEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<org.joml.Vector3f> CROSS_RIGHT = SynchedEntityData.defineId(BlackFoxBossEntity.class, EntityDataSerializers.VECTOR3);
    private long syncedSequence = -1;
    private static final EntityDataAccessor<BlockPos> ARENA = SynchedEntityData.defineId(BlackFoxBossEntity.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<org.joml.Vector3f> SLASH_AT = SynchedEntityData.defineId(BlackFoxBossEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Long> SLASH_BORN = SynchedEntityData.defineId(BlackFoxBossEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> SLASH_KIND = SynchedEntityData.defineId(BlackFoxBossEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> SLASH_YAW = SynchedEntityData.defineId(BlackFoxBossEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SLASH_ROLL = SynchedEntityData.defineId(BlackFoxBossEntity.class, EntityDataSerializers.FLOAT);
    private final BlackFoxController controller = new BlackFoxController(this);
    private UUID challenger;
    private BlockPos origin = BlockPos.ZERO;

    public BlackFoxBossEntity(EntityType<BlackFoxBossEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setCanPickUpLoot(false);
        setCustomName(Component.translatable("entity.maid_weapon.black_fox_boss"));
    }

    @Override protected void registerGoals() { }
    @Override public void lerpTo(double x,double y,double z,float yaw,float pitch,int steps,boolean teleport) {
        if(level().isClientSide && (teleport || distanceToSqr(x,y,z)>4)) {
            // The boss relocates by blinking, not by sliding between the two ends of a teleport.
            lerpSteps=0; setPos(x,y,z); setRot(yaw,pitch);
            xo=xOld=x; yo=yOld=y; zo=zOld=z; yRotO=yaw; xRotO=pitch;
        } else super.lerpTo(x,y,z,yaw,pitch,steps,teleport);
    }
    @Override public void onAddedToWorld() {
        super.onAddedToWorld();
        // SlashBlade adds a stun goal to every Mob on join. This fight owns its flinch/posture timing.
        if (!level().isClientSide) {
            goalSelector.removeAllGoals(goal -> true);
            targetSelector.removeAllGoals(goal -> true);
            BlackFoxSlashCompat.disableStun(this);
        }
    }
    public static AttributeSupplier.Builder bossAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 400)
                .add(Attributes.ATTACK_DAMAGE, 10).add(Attributes.MOVEMENT_SPEED, .3)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1).add(Attributes.ARMOR, 6);
    }
    @Override protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(ARENA,BlockPos.ZERO); entityData.define(SLASH_AT,new org.joml.Vector3f());
        entityData.define(SLASH_BORN,Long.MIN_VALUE); entityData.define(SLASH_KIND,0);
        entityData.define(SLASH_YAW,0f); entityData.define(SLASH_ROLL,0f);
        entityData.define(CROSS_LEFT, new org.joml.Vector3f()); entityData.define(CROSS_RIGHT, new org.joml.Vector3f());
        entityData.define(SWORDS_AT, 0L); entityData.define(SWORDS_MASK, 0); entityData.define(SWORDS_YAW, 0f);
        entityData.define(MOTION, 0); entityData.define(PHASE, false); entityData.define(MOTION_AT, 0L);
        entityData.define(COMBO_STAGE, 0); entityData.define(COMBO_AT, 0L);
        entityData.define(SKILL, 0); entityData.define(SKILL_AT, 0L);
        entityData.define(SINK, new org.joml.Vector3f()); entityData.define(EMERGE, new org.joml.Vector3f());
        entityData.define(STRIKE_YAW, 0f);
    }
    public void initialize(ServerPlayer player, BlockPos at) {
        challenger = player.getUUID(); origin = at.immutable();
        entityData.set(ARENA,origin);
        var spawn = FoxChallengeArena.bossSpawn(at);
        moveTo(spawn.getX() + .5, spawn.getY() + .1, spawn.getZ() + .5, 180, 0);
        setNoGravity(true);
        setItemSlot(EquipmentSlot.MAINHAND, BlackFoxSlashCompat.blade(level()));
        setDropChance(EquipmentSlot.MAINHAND, 0);
        setHealth(getMaxHealth());
    }
    @Override public void tick() {
        // Keep ordinary Mob travel/gravity; NoAi would disable those as well as decision-making.
        super.tick();
        if (!level().isClientSide) controller.tick();
    }
    @Override public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || !BlackFoxEncounters.authorized(source, this)) return false;
        if (controller.intercept(source)) return false;
        float before = getHealth();
        boolean accepted = super.hurt(source, controller.damageTaken(amount));
        if (accepted && getHealth() < before) controller.damageAccepted();
        return accepted;
    }
    @Override public void die(DamageSource source) { controller.defeat(); }
    @Override public InteractionResult mobInteract(Player player, InteractionHand hand) { return InteractionResult.FAIL; }
    @Override protected boolean shouldDespawnInPeaceful() { return false; }
    @Override public boolean removeWhenFarAway(double distance) { return false; }
    @Override public boolean canBeLeashed(Player player) { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean causeFallDamage(float distance, float multiplier, DamageSource source) { return false; }
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }
    @Override public Mob body() { return this; }
    @Override public UUID challenger() { return challenger; }
    @Override public BlockPos arenaOrigin() { return origin; }
    @Override public BlackFoxController combat() { return controller; }
    @Override public void sync(BlackFoxFight.Motion motion, boolean phaseTwo) {
        var formation = controller.ranged().formation();
        entityData.set(SWORDS_AT, formation.born());
        entityData.set(SWORDS_MASK, formation.mask());
        entityData.set(SWORDS_YAW, formation.yaw());
        if (entityData.get(MOTION) != motion.ordinal()) entityData.set(MOTION_AT, level().getGameTime());
        entityData.set(MOTION, motion.ordinal()); entityData.set(PHASE, phaseTwo);
        var combo = controller.nativeCombo();
        entityData.set(COMBO_STAGE, combo.animationStage());
        entityData.set(COMBO_AT, combo.stageStartedAt());
        var fight = controller.fight();
        if (syncedSequence != fight.sequence()) {
            syncedSequence = fight.sequence();
            entityData.set(SKILL_AT, level().getGameTime() - fight.age());
        }
        entityData.set(SKILL, fight.skill().ordinal());
        if (fight.skill() == BlackFoxFight.Skill.RIFT_CROSS) {
            var base = position();
            entityData.set(CROSS_LEFT, controller.riftCross().left().subtract(base).toVector3f());
            entityData.set(CROSS_RIGHT, controller.riftCross().right().subtract(base).toVector3f());
        }
        if (fight.skill() == BlackFoxFight.Skill.DOMAIN_CROSS) {
            var base = position(); var cross = controller.corruption().cross();
            entityData.set(CROSS_LEFT, cross.left().subtract(base).toVector3f());
            entityData.set(CROSS_RIGHT, cross.right().subtract(base).toVector3f());
        }
        if (fight.skill() == BlackFoxFight.Skill.DIMENSION_STRIKE) {
            var strike = controller.dimensionStrike();
            // Arena-local coordinates avoid float precision loss in distant private cells.
            entityData.set(SINK, strike.sink().subtract(net.minecraft.world.phys.Vec3.atLowerCornerOf(origin)).toVector3f());
            entityData.set(EMERGE, strike.emerge().subtract(net.minecraft.world.phys.Vec3.atLowerCornerOf(origin)).toVector3f());
            entityData.set(STRIKE_YAW, strike.yaw());
        }
    }
    @Override public void markSlash(BlackFoxFight.Skill skill,int age) {
        if(level().isClientSide || !BlackFoxSlashPresentation.supported(skill)) return;
        entityData.set(SLASH_AT,position().subtract(net.minecraft.world.phys.Vec3.atLowerCornerOf(origin)).toVector3f());
        entityData.set(SLASH_YAW,getYRot()); entityData.set(SLASH_ROLL,BlackFoxSlashPresentation.roll(skill,age));
        entityData.set(SLASH_KIND,skill.ordinal()); entityData.set(SLASH_BORN,level().getGameTime());
    }
    public net.minecraft.world.phys.Vec3 slashOrigin() {
        return net.minecraft.world.phys.Vec3.atLowerCornerOf(entityData.get(ARENA)).add(new net.minecraft.world.phys.Vec3(entityData.get(SLASH_AT)));
    }
    public long slashBorn() { return entityData.get(SLASH_BORN); }
    public BlackFoxFight.Skill slashKind() { return BlackFoxFight.Skill.values()[entityData.get(SLASH_KIND)]; }
    public float slashYaw() { return entityData.get(SLASH_YAW); }
    public float slashRoll() { return entityData.get(SLASH_ROLL); }
    public float slashArenaRadius() {
        var local=slashOrigin().subtract(net.minecraft.world.phys.Vec3.atBottomCenterOf(entityData.get(ARENA)));
        double side=FoxChallengeArena.RADIUS;
        return (float)Math.hypot(Math.abs(local.x)+side,Math.abs(local.z)+side)+1;
    }
    public BlackFoxFight.Skill skill() { return BlackFoxFight.Skill.values()[entityData.get(SKILL)]; }
    public long swordsStartedAt() { return entityData.get(SWORDS_AT); }
    public int swordsMask() { return entityData.get(SWORDS_MASK); }
    public float swordsYaw() { return entityData.get(SWORDS_YAW); }
    public net.minecraft.world.phys.Vec3 crossLeft() { return position().add(new net.minecraft.world.phys.Vec3(entityData.get(CROSS_LEFT))); }
    public net.minecraft.world.phys.Vec3 crossRight() { return position().add(new net.minecraft.world.phys.Vec3(entityData.get(CROSS_RIGHT))); }
    public float skillAge(float partial) { return level().getGameTime() - entityData.get(SKILL_AT) + partial; }
    public long skillStartedAt() { return entityData.get(SKILL_AT); }
    public net.minecraft.world.phys.Vec3 strikeSink() { return new net.minecraft.world.phys.Vec3(entityData.get(SINK)); }
    public net.minecraft.world.phys.Vec3 strikeEmerge() { return new net.minecraft.world.phys.Vec3(entityData.get(EMERGE)); }
    public float strikeYaw() { return entityData.get(STRIKE_YAW); }
    public long motionStartedAt() { return entityData.get(MOTION_AT); }
    public BlackFoxFight.Motion motion() { return BlackFoxFight.Motion.values()[entityData.get(MOTION)]; }
    public boolean phaseTwo() { return entityData.get(PHASE); }
    public int comboStage() { return entityData.get(COMBO_STAGE); }
    public long comboStartedAt() { return entityData.get(COMBO_AT); }
}
