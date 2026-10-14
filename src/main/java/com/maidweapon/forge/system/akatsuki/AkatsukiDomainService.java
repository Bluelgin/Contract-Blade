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
import static com.maidweapon.forge.system.akatsuki.AkatsukiDomainGrowth.has;
import static com.maidweapon.forge.network.AkatsukiDomainNetwork.*;

/** Contact observes original attacks; all independent pulse damage runs later on the server tick. */
@Mod.EventBusSubscriber(modid = "maid_weapon")
public final class AkatsukiDomainService {
    private static final Map<ServerPlayer, AkatsukiDomainSession> SESSIONS = new WeakHashMap<>();
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
    @SubscribeEvent public static void death(LivingDeathEvent e) { if (e.getEntity() instanceof ServerPlayer p) remove(p); }
    private static void remove(ServerPlayer p) { var s = SESSIONS.remove(p); if (s != null) stop(p, s); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent e) {
        SESSIONS.clear(); AkatsukiDomainAuthorization.clear(); AkatsukiDomainDamage.clear();
    }
    private AkatsukiDomainService() {}
}
