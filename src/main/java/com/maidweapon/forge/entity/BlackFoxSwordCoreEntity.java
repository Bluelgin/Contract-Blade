package com.maidweapon.forge.entity;

import com.maidweapon.forge.compat.BlackFoxSwordArtCompat;
import com.maidweapon.forge.system.fox.BlackFoxSwordCoreTimeline;
import com.maidweapon.forge.system.fox.BlackFoxSwordCoreGrowth;
import com.maidweapon.forge.system.fox.BlackFoxSwordCoreTargeting;
import static com.maidweapon.forge.system.fox.BlackFoxSwordCoreGrowth.*;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import java.util.*;

/** One owner-bound formation, independent attack/position states, native SlashBlade executors. */
public final class BlackFoxSwordCoreEntity extends Entity {
    private static final EntityDataAccessor<Integer> COMBO_AT = SynchedEntityData.defineId(BlackFoxSwordCoreEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> OWNER_FOLLOW = SynchedEntityData.defineId(BlackFoxSwordCoreEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DOMAIN_SYNC = SynchedEntityData.defineId(BlackFoxSwordCoreEntity.class, EntityDataSerializers.BOOLEAN);
    private UUID owner, bladeOrigin;
    private String binding;
    private ItemStack sourceBlade = ItemStack.EMPTY;
    private Vec3 centre = Vec3.ZERO;
    private float power;
    private Profile growth = new Profile(com.maidweapon.forge.api.ability.ContractAbilityFeatureSet.EMPTY);
    private final List<Entity> shots = new ArrayList<>();
    private final Map<UUID, BlackFoxSwordActorEntity> hunters = new LinkedHashMap<>();
    private final Map<UUID, Integer> marks = new HashMap<>();
    private final Deque<Vec3> chains = new ArrayDeque<>();
    private int chainReady, retaliateReady, retaliationUntil, domainUntil = -1, lastBarrage = -100;
    private UUID attacker;
    private BlackFoxSwordActorEntity emitter, meleeEmitter;
    public BlackFoxSwordCoreEntity(EntityType<BlackFoxSwordCoreEntity> type, net.minecraft.world.level.Level level) { super(type, level); noPhysics = true; setNoGravity(true); }
    @Override protected void defineSynchedData() { entityData.define(COMBO_AT, -1); entityData.define(OWNER_FOLLOW, false); entityData.define(DOMAIN_SYNC, false); }
    public AttackMode attackMode() { return entityData.get(COMBO_AT) >= 0 ? AttackMode.COMBO_B : AttackMode.BARRAGE; }
    public PositionMode positionMode() { return entityData.get(OWNER_FOLLOW) ? PositionMode.OWNER_FOLLOW : PositionMode.ANCHORED; }
    /** Legacy presentation accessor: means combo B, never owner movement. */
    public boolean following() { return attackMode() == AttackMode.COMBO_B; }
    public int followAge() { return following() ? tickCount - entityData.get(COMBO_AT) : -1; }
    public boolean synchronizedDomain() { return entityData.get(DOMAIN_SYNC); }
    public Vec3 centre() { return centre; }
    public int pendingChains() { return chains.size(); }
    public Profile growth() { return growth; }
    public UUID ownerId() { return owner; }
    public ItemStack sourceBlade() { return sourceBlade; }
    public void initialize(ServerPlayer player, Profile profile) {
        owner = player.getUUID(); sourceBlade = player.getMainHandItem().copy();
        bladeOrigin = sourceBlade.getTag().getUUID(com.maidweapon.forge.system.fox.FoxSpiritState.ORIGIN);
        binding = com.maidweapon.forge.system.contract.ContractCarrierData.ensureBindingId(player.getMainHandItem());
        applyGrowth(profile);
        var locked = growth.has(FOCUS) ? BlackFoxSwordArtCompat.lock(player) : null;
        centre = positionMode() == PositionMode.OWNER_FOLLOW ? player.position()
                : locked instanceof LivingEntity living && hostile(player, living) && player.distanceToSqr(living) <= 24 * 24
                ? living.position() : player.position().add(new Vec3(player.getLookAngle().x, 0, player.getLookAngle().z).normalize().scale(5));
        setPos(centre.add(0, 7, 0));
        power = (float)Math.max(4, Math.min(12, com.maidweapon.forge.compat.WhiteFoxSpecialEffectCompat.strength(sourceBlade) * .6));
    }
    public void applyGrowth(Profile profile) {
        boolean lostCombo = following() && !profile.has(COMBO);
        growth = Objects.requireNonNull(profile);
        if (lostCombo) clearAttacks();
        if (!growth.has(MARK)) marks.clear();
        if (!growth.has(CHAIN)) chains.clear();
        if (!growth.has(RETALIATE)) { attacker = null; retaliationUntil = 0; }
        entityData.set(OWNER_FOLLOW, growth.positionMode() == PositionMode.OWNER_FOLLOW);
        if (!growth.has(COMBO)) entityData.set(COMBO_AT, -1);
        if (!growth.has(SYNC)) { domainUntil = -1; entityData.set(DOMAIN_SYNC,false); }
    }
    /** Resolve the original inventory carrier, never the owner's current hand or the actor's copied item. */
    public com.maidweapon.forge.api.ContractCombatCreditResolver.Credit credit(ServerPlayer player) {
        if (player == null || !player.getUUID().equals(owner) || !player.isAlive() || player.level() != level()) return null;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            var blade = player.getInventory().getItem(slot);
            if (BlackFoxSwordArtCompat.progressionEntitled(blade, player)
                    && blade.getTag().getUUID(com.maidweapon.forge.system.fox.FoxSpiritState.ORIGIN).equals(bladeOrigin)
                    && binding.equals(com.maidweapon.forge.system.contract.ContractCarrierData.getBindingId(blade))) {
                var credit = new com.maidweapon.forge.api.ContractCombatCreditResolver.Credit(player, blade);
                if (com.maidweapon.forge.api.ContractCombatCreditResolver.valid(credit)) return credit;
            }
        }
        return null;
    }
    public static boolean hostile(ServerPlayer player, LivingEntity living) {
        if (living == player || !living.isAlive() || living instanceof net.minecraft.world.entity.player.Player
                || living instanceof TamableAnimal || living.isAlliedTo(player) || player.isAlliedTo(living)) return false;
        if (net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(living.getType()).getNamespace().equals("touhou_little_maid")) return false;
        if (living instanceof NeutralMob neutral && !neutral.isAngryAt(player)) return false;
        return living instanceof Enemy || living instanceof Mob mob && mob.getTarget() == player;
    }
    public boolean eligible(ServerPlayer player, LivingEntity living) { return eligible(player,living,attackMode()); }
    public boolean eligible(ServerPlayer player, LivingEntity living, AttackMode mode) {
        double radius = growth.radius(mode);
        return hostile(player,living) && living.level() == level() && living.position().distanceToSqr(centre) <= radius * radius
                && level().clip(new net.minecraft.world.level.ClipContext(position(), living.getEyePosition(), net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, this)).getType() == net.minecraft.world.phys.HitResult.Type.MISS;
    }
    public boolean marked(LivingEntity living) { return marks.getOrDefault(living.getUUID(),0) > tickCount; }
    public int priority(ServerPlayer player, LivingEntity living) {
        var locked = growth.has(FOCUS) ? BlackFoxSwordArtCompat.lock(player) : null;
        return BlackFoxSwordCoreTargeting.priority(growth,
                tickCount < retaliationUntil && living.getUUID().equals(attacker), locked == living,
                player.distanceToSqr(living) <= 4 * 4 || living instanceof Mob mob && mob.getTarget() == player, marked(living));
    }
    public List<LivingEntity> targets(ServerPlayer player, AttackMode mode) {
        return level().getEntitiesOfClass(LivingEntity.class, new AABB(centre, centre).inflate(growth.radius(mode)), living -> eligible(player, living,mode)).stream()
                .sorted(Comparator.<LivingEntity>comparingInt(living -> priority(player,living)).reversed()
                        .thenComparingDouble(living -> living.position().distanceToSqr(centre)).thenComparing(Entity::getUUID))
                .limit(growth.count("max_targets",8)).toList();
    }
    public LivingEntity followTarget(ServerPlayer player) { var targets = targets(player,AttackMode.COMBO_B); return targets.isEmpty() ? null : targets.get(0); }
    public void switchAttackMode() {
        if (!growth.has(COMBO) || !BlackFoxSwordCoreTimeline.canFollow(tickCount,following())) return;
        clearAttacks();
        entityData.set(COMBO_AT, following() ? -1 : tickCount);
        domainUntil = growth.has(SYNC) ? tickCount + growth.count("sync_duration",24) : -1;
        entityData.set(DOMAIN_SYNC,growth.has(SYNC));
        lastBarrage = tickCount; // No free burst from rapid toggles.
    }
    /** Compatibility with existing test/presentation callers. */
    public void follow(LivingEntity ignored) { switchAttackMode(); }
    public void capture(Entity shot) {
        shots.removeIf(Entity::isRemoved);
        if (shots.size() >= 96) shot.discard(); else shots.add(shot);
    }
    public void onNativeHit(ServerPlayer player, LivingEntity target) {
        if (growth.has(MARK) && eligible(player,target,AttackMode.BARRAGE)) {
            if (marks.size() < 64 || marks.containsKey(target.getUUID())) marks.put(target.getUUID(),tickCount + growth.count("mark_duration",80));
        }
    }
    public void onMarkedDeath(LivingEntity target) {
        Integer expiry = marks.remove(target.getUUID());
        boolean marked = expiry != null && expiry > tickCount;
        if (marked && growth.has(CHAIN) && tickCount >= chainReady && chains.size() < 2
                && target.position().distanceToSqr(centre) <= growth.barrageRadius() * growth.barrageRadius()) {
            chains.add(target.position()); chainReady = tickCount + growth.count("chain_cooldown",40);
        }
    }
    public void onOwnerHurt(ServerPlayer player, LivingEntity enemy) {
        if (!growth.has(RETALIATE) || !eligible(player,enemy,attackMode())) return;
        attacker = enemy.getUUID(); retaliationUntil = tickCount + growth.count("retaliate_window",60);
        if (tickCount >= retaliateReady) {
            retaliateReady = tickCount + growth.count("retaliate_cooldown",24);
            if (following()) allocateHunters(player,targets(player,AttackMode.COMBO_B));
            else lastBarrage = Math.min(lastBarrage, tickCount-growth.count("barrage_interval",12));
            feedback(enemy.position().add(0,1,0), 8);
        }
    }
    @Override public void tick() {
        super.tick(); if (level().isClientSide) return;
        if (synchronizedDomain() && tickCount >= domainUntil) entityData.set(DOMAIN_SYNC,false);
        shots.removeIf(Entity::isRemoved); marks.entrySet().removeIf(entry -> entry.getValue() <= tickCount);
        var level = (net.minecraft.server.level.ServerLevel)level();
        ServerPlayer player = owner == null ? null : level.getServer().getPlayerList().getPlayer(owner);
        if (player == null || !player.isAlive() || player.level() != level || credit(player) == null || tickCount >= BlackFoxSwordCoreTimeline.DURATION) { discard(); return; }
        // An existing black-fox formation is maintained every five seconds, not
        // charged for each native projectile or target hit.
        if (tickCount % 100 == 0) {
            var verified = credit(player);
            if (verified == null || !com.maidweapon.forge.system.resonance.SpiritAbilityResonanceCosts.pay(
                    player, verified.carrier(),
                    com.maidweapon.forge.system.resonance.SpiritAbilityResonanceCosts.Action.BLACK_UPKEEP)) {
                discard();
                return;
            }
        }
        if (positionMode() == PositionMode.OWNER_FOLLOW) {
            // Seven blocks is an invariant; horizontal lag gives inertia without dropping below tall bosses.
            Vec3 desired = player.position();
            centre = centre.distanceToSqr(desired) > 24 * 24 ? desired
                    : new Vec3(centre.x+(desired.x-centre.x)*.35, desired.y, centre.z+(desired.z-centre.z)*.35);
            setPos(centre.add(0,7,0));
        } else if (player.distanceToSqr(this) > 64 * 64) { discard(); return; }
        if (tickCount < BlackFoxSwordCoreTimeline.DEPLOY) return;
        if (following() || synchronizedDomain()) allocateHunters(player,targets(player,AttackMode.COMBO_B));
        else clearHunters();
        if ((!following() || synchronizedDomain()) && tickCount-lastBarrage >= growth.count("barrage_interval",12)) {
            var targets = targets(player,AttackMode.BARRAGE);
            if (!targets.isEmpty()) {
                lastBarrage = tickCount;
                // During a mode transition the secondary mode receives only one quarter of the normal barrage budget.
                int budget = synchronizedDomain() ? 1 : growth.count("projectile_count",4);
                barrage(player,targets,budget);
            }
        }
        while (!chains.isEmpty()) {
            Vec3 from = chains.removeFirst();
            var candidates = targets(player,attackMode());
            if (!candidates.isEmpty()) {
                feedback(from.add(0,1,0),8);
                for (int shot=0; shot<growth.count("chain_count",2); shot++)
                    fire(player,candidates.get(shot % candidates.size()),from.add(0,1,0),0, false,attackMode());
            }
        }
        if (tickCount % 10 == 0 && !marks.isEmpty()) for (var enemy : targets(player,AttackMode.BARRAGE))
            if (marked(enemy)) feedback(enemy.getEyePosition().add(0,.4,0),1);
    }
    private void allocateHunters(ServerPlayer player, List<LivingEntity> enemies) {
        // Keep a valid actor through its whole native B chain. Distance-order changes must not restart B1.
        hunters.entrySet().removeIf(entry -> {
            boolean remove = entry.getValue().isRemoved() || !entry.getValue().validVictim();
            if (remove) entry.getValue().discard(); return remove;
        });
        while (hunters.size() > growth.hunters()) removeLowestHunter(player);
        for (var enemy : enemies) if (!hunters.containsKey(enemy.getUUID())) {
            if (hunters.size() >= growth.hunters()) {
                var lowest = lowestHunter(player);
                // An explicit lock or fresh attacker may redirect a busy formation immediately.
                if (priority(player,enemy) < 3 || lowest == null || priority(player,enemy) <= priority(player,lowest.victim())) continue;
                hunters.remove(lowest.victim().getUUID()); lowest.discard();
            }
            if (hunters.size() < growth.hunters()) {
                var actor = com.maidweapon.forge.compat.fox.BlackFoxBossCompat.spawnSwordActor(this,player,enemy);
                if (actor != null) hunters.put(enemy.getUUID(),actor);
            }
        }
    }
    private BlackFoxSwordActorEntity lowestHunter(ServerPlayer player) {
        return hunters.values().stream().min(Comparator.comparingInt(actor -> priority(player,actor.victim()))).orElse(null);
    }
    private void removeLowestHunter(ServerPlayer player) {
        var actor = lowestHunter(player); if (actor != null) { hunters.remove(actor.victim().getUUID()); actor.discard(); }
    }
    private void barrage(ServerPlayer player, List<LivingEntity> targets, int budget) {
        for (int shot=0; shot<budget; shot++) {
            int cycle = tickCount/growth.count("barrage_interval",12)+shot;
            int kind = cycle % 4;
            if (kind == 3 && (!growth.has(WAVE) || budget-shot < 2)) kind = 0;
            if (kind == 2 && !growth.has(VERTICAL)) kind = 0;
            if (kind == 1 && !growth.has(DRIVE)) kind = 0;
            // Half the fire stays focused; the remainder continues broad suppression.
            var target = shot % 2 == 0 ? targets.get(0) : targets.get(cycle % targets.size());
            Vec3 from = position().add(Math.cos(cycle*Math.PI/3)*2.4,0,Math.sin(cycle*Math.PI/3)*2.4);
            fire(player,target,from,kind,true,AttackMode.BARRAGE);
            if (kind == 3) shot++; // Native WaveEdge(count=1) emits TWO drives, charged against the same budget.
        }
    }
    private void fire(ServerPlayer player, LivingEntity living, Vec3 from, int kind, boolean full, AttackMode scope) {
        if (shots.size() >= 46 || !eligible(player,living,scope)) return;
        var actor = scope == AttackMode.COMBO_B ? meleeEmitter : emitter;
        if (actor == null || actor.isRemoved()) {
            actor = com.maidweapon.forge.compat.fox.BlackFoxBossCompat.spawnSwordActor(this,player,null);
            if (actor == null) return;
            actor.setEmitterMode(scope);
            if (scope == AttackMode.COMBO_B) meleeEmitter = actor; else emitter = actor;
        }
        if (kind == 0) {
            var shot = com.maidweapon.forge.compat.BlackFoxPhantomCompat.spawn(actor,from,living.getEyePosition().subtract(from).normalize(),false,1.25f);
            com.maidweapon.forge.compat.BlackFoxPhantomCompat.damage(shot,
                power * growth.number("core_damage_multiplier",1) * (full ? .35 : .2));
        } else com.maidweapon.forge.compat.BlackFoxCoreNativeBarrage.fire(actor,from,living.getEyePosition(),kind,
                power * growth.number("core_damage_multiplier",1) * .6);
    }
    private void feedback(Vec3 at,int count) {
        ((net.minecraft.server.level.ServerLevel)level()).sendParticles(new net.minecraft.core.particles.DustParticleOptions(
                new org.joml.Vector3f(.69f,.30f,.90f),1.1f),at.x,at.y,at.z,count,.18,.18,.18,0);
    }
    private void clearHunters() { hunters.values().forEach(Entity::discard); hunters.clear(); }
    private void clearAttacks() {
        shots.forEach(Entity::discard); shots.clear(); clearHunters();
        if (emitter != null) { emitter.discard(); emitter = null; }
        if (meleeEmitter != null) { meleeEmitter.discard(); meleeEmitter = null; }
    }
    @Override public void remove(RemovalReason reason) { clearAttacks(); marks.clear(); chains.clear(); super.remove(reason); }
    @Override protected void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) { }
    @Override protected void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) { }
    @Override public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getAddEntityPacket() { return net.minecraftforge.network.NetworkHooks.getEntitySpawningPacket(this); }
}
