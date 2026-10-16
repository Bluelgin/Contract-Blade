package com.maidweapon.forge.system.mastery;

import com.maidweapon.forge.api.ContractCombatCreditResolver;
import com.maidweapon.forge.system.contract.ContractCarrierData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.HashMap;

/** Ephemeral target/launch proof. Death/logout invalidates pending challenges; earned counters keep original semantics. */
@Mod.EventBusSubscriber(modid = "maid_weapon")
public final class ContractCombatCredits {
    private record Stamp(ContractCombatCreditResolver.Credit credit, String binding, String maid, long generation) { }
    private record Launch(Stamp stamp, UUID shooter) { }
    private static final Map<Entity, Launch> LAUNCHES = new WeakHashMap<>();
    private static final Map<LivingEntity, Stamp> HITS = new WeakHashMap<>();
    private static final Map<UUID, Long> GENERATIONS = new HashMap<>();
    private static final java.util.Set<LivingEntity> SETTLED = java.util.Collections.newSetFromMap(new WeakHashMap<>());
    private static Stamp stamp(ContractCombatCreditResolver.Credit credit) {
        if (!ContractCombatCreditResolver.valid(credit)) return null;
        return new Stamp(credit, ContractCarrierData.ensureBindingId(credit.carrier()),
                ContractCarrierData.getBoundMaidUUID(credit.carrier()), GENERATIONS.getOrDefault(credit.player().getUUID(), 0L));
    }
    private static boolean valid(Stamp stamp) {
        if (stamp == null || !ContractCombatCreditResolver.valid(stamp.credit())) return false;
        return stamp.generation() == GENERATIONS.getOrDefault(stamp.credit().player().getUUID(), 0L)
                && stamp.binding().equals(ContractCarrierData.getBindingId(stamp.credit().carrier()))
                && java.util.Objects.equals(stamp.maid(), ContractCarrierData.getBoundMaidUUID(stamp.credit().carrier()));
    }
    /** Third-party launch hook: server-created entity + actual carrier, never NBT supplied by clients. */
    public static void stampLaunch(Entity entity, ContractCombatCreditResolver.Credit credit) {
        if (entity == null || credit == null || credit.player() == null || entity.level().isClientSide || entity.level() != credit.player().level()) return;
        Stamp stamp = stamp(credit);
        Entity shooter = entity instanceof Projectile projectile ? projectile.getOwner() : null;
        if (stamp != null) LAUNCHES.put(entity, new Launch(stamp, shooter == null ? null : shooter.getUUID()));
    }
    @SubscribeEvent public static void join(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide || !(event.getEntity() instanceof Projectile shot)) return;
        var credit = ContractCombatCreditResolver.actor(shot.getOwner());
        // Verified native SlashBlade projectile ownership. Other launchers need an explicit stampLaunch adapter.
        if (credit != null && net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(shot.getType()).getNamespace().equals("slashblade")
                && com.maidweapon.forge.compat.SlashBladeCompat.isSlashBlade(credit.carrier())) stampLaunch(shot, credit);
    }
    private static Stamp resolve(DamageSource source) {
        // Native slash/ judgement areaAttack rewrites its DamageSource to the shooter. The existing
        // optional mixin brackets its tick with the actual projectile; never infer from today's held blade.
        Entity scoped = com.maidweapon.forge.system.fox.challenge.BlackFoxAttackTrace.projectile();
        Entity origin = scoped == null ? source.getDirectEntity() : scoped;
        Launch launch = LAUNCHES.get(origin);
        if (launch != null) {
            if (scoped != null && source.getEntity() == null) return null;
            if (source.getEntity() != null && launch.shooter() != null
                    && !source.getEntity().getUUID().equals(launch.shooter())) return null;
            Entity current = origin instanceof Projectile projectile ? projectile.getOwner() : null;
            if (current != null && launch.shooter() != null && !current.getUUID().equals(launch.shooter())) return null;
            return valid(launch.stamp()) ? launch.stamp() : null;
        }
        if (scoped != null) return null; // An unstamped area attack cannot borrow the current hand.
        return stamp(ContractCombatCreditResolver.direct(source));
    }
    /** Read-only reuse of the launch/current-carrier proof for on-hit abilities. */
    public static ContractCombatCreditResolver.Credit resolveHit(DamageSource source) {
        Stamp proof = resolve(source);
        return valid(proof) ? proof.credit() : null;
    }
    public static void record(LivingEntity target, DamageSource source) {
        Stamp stamp = resolve(source);
        if (valid(stamp) && !SETTLED.contains(target)) HITS.put(target, stamp);
        else HITS.remove(target); // A later ordinary/foreign hit cannot inherit an earlier contract credit.
    }
    public static ContractCombatCreditResolver.Credit settle(LivingEntity target, DamageSource source) {
        if (SETTLED.contains(target)) return null;
        Stamp hit = HITS.remove(target), killer = resolve(source);
        if (!valid(hit) || !valid(killer) || !hit.credit().player().getUUID().equals(killer.credit().player().getUUID())
                || !hit.binding().equals(killer.binding())) return null;
        SETTLED.add(target); return killer.credit();
    }
    public static void invalidate(UUID owner) { GENERATIONS.merge(owner, 1L, Long::sum); }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID owner=event.getEntity().getUUID();
        LAUNCHES.values().removeIf(v->v.stamp().credit().player().getUUID().equals(owner));
        HITS.values().removeIf(v->v.credit().player().getUUID().equals(owner));
        GENERATIONS.remove(owner);
        com.maidweapon.forge.system.fox.challenge.BlackFoxAttackTrace.forget(owner);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { LAUNCHES.clear(); HITS.clear(); GENERATIONS.clear(); SETTLED.clear(); }
    private ContractCombatCredits() { }
}
