package com.maidweapon.forge.system.akatsuki;

import com.maidweapon.common.AkatsukiDomainConfig;
import com.maidweapon.forge.api.ability.ContractAbilityFeatureSet;
import com.maidweapon.forge.compat.AkatsukiCombatCompat;
import com.maidweapon.forge.network.AkatsukiDomainNetwork;
import com.maidweapon.forge.system.contract.ContractCarrierData;
import com.maidweapon.forge.system.mastery.ContractCombatCredits;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import java.util.*;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.projectile.Projectile;
import static com.maidweapon.forge.system.akatsuki.AkatsukiDomainGrowth.has;
import static com.maidweapon.forge.network.AkatsukiDomainNetwork.*;

/** Contact observes original attacks; all independent pulse damage runs later on the server tick. */
@Mod.EventBusSubscriber(modid = "maid_weapon")
public final class AkatsukiDomainService {
    private static final Map<ServerPlayer, AkatsukiDomainSession> SESSIONS = new WeakHashMap<>();
    private record Chain(Vec3 center, long expires) {}
    private static final Map<ServerPlayer, Chain> CHAINS = new WeakHashMap<>();
    private static boolean chaining;
    public static boolean hostile(ServerPlayer player, LivingEntity target) {
        return target != player && target.isAlive() && !target.isInvulnerable() && !(target instanceof Player)
                && !(target instanceof TamableAnimal) && !player.isAlliedTo(target) && !target.isAlliedTo(player)
                && (target instanceof Enemy || target instanceof Mob mob && mob.getTarget() == player)
                && player.level() == target.level();
    }
    private static long now(ServerPlayer player) { return player.getServer().overworld().getGameTime(); }
    private static void contact(ServerPlayer player, LivingEntity target) {
        if (AkatsukiDomainDamage.applying() || !hostile(player, target)
                || player.distanceToSqr(target) > AkatsukiDomainConfig.RADIUS.get() * AkatsukiDomainConfig.RADIUS.get()
                || !player.hasLineOfSight(target)) return;
        var features = AkatsukiDomainGrowth.capabilities(player);
        if (!has(features, "garden")) return;
        var state = SESSIONS.computeIfAbsent(player, p -> new AkatsukiDomainSession());
        String binding = ContractCarrierData.getBindingId(player.getMainHandItem());
        if (!state.binding.equals(binding)) state.suspend();
        state.binding = binding;
        if (state.contact(now(player), target.getUUID(), AkatsukiDomainConfig.OPEN_HITS.get(),
                AkatsukiDomainConfig.CONTACT_GAP.get(), AkatsukiDomainConfig.DURATION.get(), AkatsukiDomainConfig.COOLDOWN.get()))
            cue(player, state, features, STATE, target);
    }
    /** Explicit melee contact remains observable when a boss rejects hurt() before Forge events. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void melee(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getTarget() instanceof LivingEntity target)
                || player.getAttackStrengthScale(.5f) < .5f) return;
        double reach = player.getAttributeValue(net.minecraftforge.common.ForgeMod.ENTITY_REACH.get());
        if (target.getBoundingBox().inflate(.3).distanceToSqr(player.getEyePosition()) <= reach * reach) contact(player, target);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void hit(LivingDamageEvent event) {
        if (event.getAmount() <= 0 || AkatsukiDomainDamage.domain(event.getSource())
                || AkatsukiCombatCompat.secondary(event.getSource())) return;
        var credit = ContractCombatCredits.resolveHit(event.getSource());
        if (credit != null && credit.carrier() == credit.player().getMainHandItem()) contact(credit.player(), event.getEntity());
    }
    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        var state = SESSIONS.get(player);
        if (state == null) return;
        var features = AkatsukiDomainGrowth.capabilities(player);
        long now = now(player);
        if (!has(features, "garden") || !state.binding.equals(ContractCarrierData.getBindingId(player.getMainHandItem()))
                || !state.active(now) || now - state.lastContact > AkatsukiDomainConfig.CONTACT_GAP.get()) {
            stop(player, state); return;
        }
        if (now % 5 != 0) return; // No broad entity query each tick.
        Entity found = state.target == null ? null : player.serverLevel().getEntity(state.target);
        LivingEntity target = found instanceof LivingEntity living && validTarget(player, living) ? living : null;
        if (state.due("snapshot", now, 10)) cue(player, state, features, STATE, target);
        if (target != null && state.due("edge", now, AkatsukiDomainConfig.EDGE_INTERVAL.get())) {
            cue(player, state, features, EDGE, target);
            AkatsukiDomainDamage.pulse(player, target, state, has(features, "adaptation"));
        }
        if (has(features, "swords") && target != null && state.due("swords", now, AkatsukiDomainConfig.SWORD_INTERVAL.get())) {
            cue(player, state, features, SWORDS, target);
            AkatsukiDomainDamage.pulse(player, target, state, has(features, "adaptation"));
        }
        if (has(features, "ring") && state.due("ring", now, AkatsukiDomainConfig.RING_INTERVAL.get())) {
            cue(player, state, features, RING, null);
            for (var enemy : nearby(player, Math.min(5, AkatsukiDomainConfig.RADIUS.get())))
                AkatsukiDomainDamage.pulse(player, enemy, state, has(features, "adaptation"));
        }
        if (has(features, "tracking") && state.due("tracking", now, 20)) {
            if (target == null) {
                var targets = nearby(player, AkatsukiDomainConfig.RADIUS.get());
                if (!targets.isEmpty()) { target = targets.get(0); state.target = target.getUUID(); }
            }
            if (target != null) cue(player, state, features, LOCK, target);
        }
        if (has(features, "counter") && state.due("counter", now, AkatsukiDomainConfig.COUNTER_INTERVAL.get()))
            counter(player, state, features);
        var chain = CHAINS.remove(player);
        if (has(features, "chain") && chain != null && now <= chain.expires()) {
            chaining = true;
            try {
                for (var enemy : nearby(player, AkatsukiDomainConfig.RADIUS.get()).stream()
                        .filter(t -> t.position().distanceToSqr(chain.center()) <= 16).limit(3).toList()) {
                    cue(player, state, features, CHAIN, enemy);
                    AkatsukiDomainDamage.pulse(player, enemy, state, has(features, "adaptation"));
                }
            } finally { chaining = false; }
        }
        if (has(features, "cloak") && state.due("cloak", now, AkatsukiDomainConfig.CLOAK_INTERVAL.get())) {
            state.cloakUntil = now + 20;
            cue(player, state, features, CLOAK, null);
        }
        if (has(features, "rain") && state.due("rain", now, AkatsukiDomainConfig.RAIN_INTERVAL.get())) {
            cue(player, state, features, RAIN, target);
            for (var enemy : nearby(player, AkatsukiDomainConfig.RADIUS.get()))
                AkatsukiDomainDamage.pulse(player, enemy, state, has(features, "adaptation"));
        }
        if (has(features, "complete") && state.due("complete", now, 80)) {
            cue(player, state, features, FINAL, target);
            // One focused pulse; the client unfolds dozens of converging cuts independently.
            if (target != null) AkatsukiDomainDamage.pulse(player, target, state, has(features, "adaptation"));
        }
    }
    private static boolean validTarget(ServerPlayer player, LivingEntity target) {
        double radius = AkatsukiDomainConfig.RADIUS.get();
        return hostile(player, target) && player.distanceToSqr(target) <= radius * radius && player.hasLineOfSight(target);
    }
    private static List<LivingEntity> nearby(ServerPlayer player, double radius) {
        return player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius),
                        t -> validTarget(player, t) && player.distanceToSqr(t) <= radius * radius)
                .stream().sorted(Comparator.comparingDouble(player::distanceToSqr))
                .limit(AkatsukiDomainConfig.MAX_TARGETS.get()).toList();
    }
    private static void counter(ServerPlayer player, AkatsukiDomainSession state, ContractAbilityFeatureSet features) {
        var shots = player.level().getEntitiesOfClass(Projectile.class, player.getBoundingBox().inflate(4),
                shot -> (shot.getType() == EntityType.ARROW || shot.getType() == EntityType.SPECTRAL_ARROW
                        || shot.getType() == EntityType.SMALL_FIREBALL)
                        && shot.getOwner() instanceof LivingEntity owner && hostile(player, owner)
                        && shot.getDeltaMovement().dot(player.getEyePosition().subtract(shot.position())) > 0
                        && player.hasLineOfSight(shot));
        if (shots.isEmpty()) return;
        var shot = shots.get(0);
        var owner = (LivingEntity) shot.getOwner();
        shot.discard(); // Explicit vanilla whitelist, one projectile per interval; no boss projectile removal.
        cue(player, state, features, COUNTER, owner);
        if (validTarget(player, owner)) AkatsukiDomainDamage.pulse(player, owner, state, has(features, "adaptation"));
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void cloak(LivingHurtEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && protectedByCloak(p)
                && !e.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY))
            e.setAmount(Math.max(0, e.getAmount() - Math.min(2, e.getAmount() * .15f)));
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void knockback(LivingKnockBackEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && protectedByCloak(p)) e.setCanceled(true);
    }
    private static boolean protectedByCloak(ServerPlayer p) {
        var s = SESSIONS.get(p);
        return s != null && s.active(now(p)) && now(p) < s.cloakUntil
                && now(p) - s.lastContact <= AkatsukiDomainConfig.CONTACT_GAP.get()
                && has(AkatsukiDomainGrowth.capabilities(p), "cloak");
    }
    private static void cue(ServerPlayer player, AkatsukiDomainSession state, ContractAbilityFeatureSet f, int kind, LivingEntity target) {
        AkatsukiDomainNetwork.send(player, kind, target == null ? player.position() : target.getBoundingBox().getCenter(),
                AkatsukiDomainConfig.RADIUS.get().floatValue(), f, (int) Math.max(0, state.expires - now(player)));
    }
    private static void stop(ServerPlayer player, AkatsukiDomainSession state) {
        if (state.expires != 0) {
            AkatsukiDomainNetwork.send(player, END, player.position(), 0, ContractAbilityFeatureSet.EMPTY, 0);
            state.suspend();
        }
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { if (e.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { if (e.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void death(LivingDeathEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) { remove(p); return; }
        AkatsukiDomainDamage.forget(e.getEntity());
        if (chaining || !(e.getSource().getEntity() instanceof ServerPlayer p)) return;
        var state = SESSIONS.get(p);
        if (state == null || !state.active(now(p)) || !has(AkatsukiDomainGrowth.capabilities(p), "chain")
                || p.distanceToSqr(e.getEntity()) > AkatsukiDomainConfig.RADIUS.get() * AkatsukiDomainConfig.RADIUS.get()
                || !(e.getEntity() instanceof Enemy) || e.getEntity().isAlliedTo(p)
                || !AkatsukiDomainDamage.domain(e.getSource()) && (AkatsukiCombatCompat.secondary(e.getSource())
                    || ContractCombatCredits.resolveHit(e.getSource()) == null)
                || !state.due("chain", now(p), AkatsukiDomainConfig.CHAIN_INTERVAL.get())) return;
        CHAINS.put(p, new Chain(e.getEntity().position(), now(p) + 10));
    }
    @SubscribeEvent public static void serverTick(TickEvent.ServerTickEvent e) {
        if (e.phase == TickEvent.Phase.END && e.getServer().getTickCount() % 40 == 0)
            AkatsukiDomainDamage.prune(e.getServer().overworld().getGameTime());
    }
    @SubscribeEvent public static void leave(net.minecraftforge.event.entity.EntityLeaveLevelEvent e) {
        if (!e.getLevel().isClientSide() && e.getEntity() instanceof LivingEntity living) AkatsukiDomainDamage.forget(living);
    }
    private static void remove(ServerPlayer p) { CHAINS.remove(p); var s = SESSIONS.remove(p); if (s != null) stop(p, s); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent e) {
        SESSIONS.clear(); CHAINS.clear(); AkatsukiDomainAuthorization.clear(); AkatsukiDomainDamage.clear();
    }
    private AkatsukiDomainService() {}
}
