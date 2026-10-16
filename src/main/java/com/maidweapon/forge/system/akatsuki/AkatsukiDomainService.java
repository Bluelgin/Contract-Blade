package com.maidweapon.forge.system.akatsuki;

import static com.maidweapon.forge.network.AkatsukiDomainNetwork.*;
import static com.maidweapon.forge.system.akatsuki.AkatsukiDomainGrowth.has;

import com.maidweapon.common.AkatsukiDomainConfig;
import com.maidweapon.forge.api.ability.ContractAbilityFeatureSet;
import com.maidweapon.forge.compat.AkatsukiCombatCompat;
import com.maidweapon.forge.network.AkatsukiDomainNetwork;
import com.maidweapon.forge.system.contract.ContractCarrierData;
import com.maidweapon.forge.system.mastery.ContractCombatCredits;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

/**
 * Health drives lifecycle; contact only supplies targets. Bounded damage runs on the server tick.
 */
@Mod.EventBusSubscriber(modid = "maid_weapon")
public final class AkatsukiDomainService {
  private static final Map<ServerPlayer, AkatsukiDomainSession> SESSIONS = new WeakHashMap<>();

  private record Chain(Vec3 center, long expires) {}

  private static final Map<ServerPlayer, Chain> CHAINS = new WeakHashMap<>();
  private static boolean chaining;

  public static boolean hostile(ServerPlayer player, LivingEntity target) {
    return target != player
        && target.isAlive()
        && !(target instanceof Player)
        && !(target instanceof TamableAnimal)
        && !player.isAlliedTo(target)
        && !target.isAlliedTo(player)
        && (target instanceof Enemy || target instanceof Mob mob && mob.getTarget() == player)
        && player.level() == target.level();
  }

  /** Only player-directed strikes can establish a temporary non-hostile training target. */
  private static boolean trainingTarget(ServerPlayer player, LivingEntity target) {
    if (!com.maidweapon.common.SoulDamageConfig.TRAINING_TARGETS.get()
        || target == player
        || target instanceof Player
        || target instanceof TamableAnimal
        || player.isAlliedTo(target)
        || target.isAlliedTo(player)) return false;
    var type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(target.getType());
    return AkatsukiTrainingTargetRules.matches(
        type.toString(),
        com.maidweapon.common.SoulDamageConfig.EXTRA_TRAINING_TYPES.get());
  }

  public static boolean combatTarget(
      ServerPlayer player, LivingEntity target, AkatsukiDomainSession session) {
    return hostile(player, target)
        || session != null
            && target.getUUID().equals(session.target)
            && now(player) - session.lastContact <= AkatsukiDomainConfig.CONTACT_GAP.get()
            && trainingTarget(player, target);
  }

  private static long now(ServerPlayer player) {
    return player.getServer().overworld().getGameTime();
  }

  private static void contact(ServerPlayer player, LivingEntity target) {
    if (AkatsukiDomainDamage.applying()
        || !(hostile(player, target) || trainingTarget(player, target))
        || player.distanceToSqr(target)
            > AkatsukiDomainConfig.RADIUS.get() * AkatsukiDomainConfig.RADIUS.get()
        || !player.hasLineOfSight(target)) return;
    var features = AkatsukiDomainGrowth.capabilities(player);
    if (!has(features, "garden")) return;
    var state = refresh(player, features);
    if (state.attacking() && state.contact(now(player), target.getUUID()))
      cue(player, state, features, EDGE, target);
  }

  private static AkatsukiDomainSession refresh(ServerPlayer player, ContractAbilityFeatureSet f) {
    var state = SESSIONS.computeIfAbsent(player, p -> new AkatsukiDomainSession());
    // Switching to an unbound item/empty hand returns null. Rebind closes the old
    // domain (sending END) before the old session can be resumed with new authority.
    state.rebind(ContractCarrierData.getBindingId(player.getMainHandItem()),
        () -> stop(player, state));
    boolean changed =
        state.lifecycle.update(
            has(f, "garden") && !state.binding.isBlank() && !player.isSpectator(),
            player.isAlive(),
            player.getHealth() / Math.max(1f, player.getMaxHealth()),
            now(player),
            AkatsukiDomainConfig.RAMPAGE_ENTER.get(),
            AkatsukiDomainConfig.RAMPAGE_EXIT.get());
    if (changed) {
      if (!state.active()) state.suspend();
      cue(
          player,
          state,
          f,
          !state.active()
              ? END
              : state.lifecycle.phase() == AkatsukiDomainState.Phase.RESCUE_REIGNITE
                  ? REIGNITE
                  : STATE,
          null);
    }
    return state;
  }

  /** Visual-only response to a confirmed native-hit resonance, no extra damage call. */
  public static void resonanceCue(ServerPlayer player, LivingEntity target) {
    var session = SESSIONS.get(player);
    if (session != null && session.attacking()
        && session.binding.equals(ContractCarrierData.getBindingId(player.getMainHandItem())))
      cue(player, session, AkatsukiDomainGrowth.capabilities(player), EDGE, target);
  }

  /** Refreshes the same authoritative lifecycle used by automatic cuts and rescue. */
  public static AkatsukiDomainSession session(ServerPlayer player) {
    return refresh(player, AkatsukiDomainGrowth.capabilities(player));
  }

  /** Explicit melee contact remains observable when a boss rejects hurt() before Forge events. */
  @SubscribeEvent(priority = EventPriority.LOWEST)
  public static void melee(AttackEntityEvent event) {
    if (!(event.getEntity() instanceof ServerPlayer player)
        || !(event.getTarget() instanceof LivingEntity target)
        || player.getAttackStrengthScale(.5f) < .5f) return;
    double reach = player.getAttributeValue(net.minecraftforge.common.ForgeMod.ENTITY_REACH.get());
    if (target.getBoundingBox().inflate(.3).distanceToSqr(player.getEyePosition()) <= reach * reach)
      contact(player, target);
  }

  @SubscribeEvent(priority = EventPriority.LOWEST)
  public static void hit(LivingDamageEvent event) {
    // Observe an actual native pulse, not private thresholds or execution collections.
    if (event.getAmount() > 0
        && event
            .getSource()
            .typeHolder()
            .unwrapKey()
            .map(k -> k.location().toString().equals("blade_tetra:akatsuki_execution"))
            .orElse(false)
        && event.getSource().getEntity() instanceof ServerPlayer p) {
      var session = SESSIONS.get(p);
      if (session != null && session.active()) {
        session.executionPulseUntil = now(p) + 20;
        if (Boolean.getBoolean("contractblade.akatsukiVisual.validation"))
          com.mojang.logging.LogUtils.getLogger().info("AKATSUKI_NATIVE_EXECUTION_OBSERVED");
        var f = AkatsukiDomainGrowth.capabilities(p);
        if (has(f, "complete") && session.due("execution_visual", now(p), 10))
          cue(p, session, f, EXECUTION, event.getEntity());
      }
      return;
    }
    if (event.getAmount() <= 0
        || AkatsukiDomainDamage.domain(event.getSource())
        || AkatsukiCombatCompat.secondary(event.getSource())) return;
    var credit = ContractCombatCredits.resolveHit(event.getSource());
    if (credit != null && credit.carrier() == credit.player().getMainHandItem())
      contact(credit.player(), event.getEntity());
  }

  @SubscribeEvent
  public static void tick(TickEvent.PlayerTickEvent event) {
    if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player))
      return;
    var features = AkatsukiDomainGrowth.capabilities(player);
    var state = SESSIONS.get(player);
    if (state == null && !has(features, "garden")) return;
    state = refresh(player, features);
    long now = now(player);
    if (!state.active()) {
      CHAINS.remove(player);
      return;
    }
    if (state.attacking()
        && player.getHealth() <= player.getMaxHealth() * AkatsukiDomainConfig.RESCUE_THRESHOLD.get()
        && player.getY() >= player.level().getMinBuildHeight() - 64
        && (player.getLastDamageSource() == null || rescuable(player.getLastDamageSource())))
      rescue(player, state, features);
    if (state.due("snapshot", now, 5)) cue(player, state, features, STATE, null);
    if (!state.attacking() || now - state.lastContact > AkatsukiDomainConfig.CONTACT_GAP.get()) {
      CHAINS.remove(player);
      return; // Idle domains do not scan, damage or make combat effects.
    }
    if (now % 2 != 0) return;
    Entity found = state.target == null ? null : player.serverLevel().getEntity(state.target);
    LivingEntity target =
        found instanceof LivingEntity living && validTarget(player, living) ? living : null;

    if (target != null
        && state.due("edge", now, com.maidweapon.common.SoulDamageConfig.CUT_GAP.get())) {
      AkatsukiDomainDamage.pulse(player, target, state, has(features, "adaptation"));
    }
    if (has(features, "ring")
        && state.due("ring", now, interval(state, AkatsukiDomainConfig.RING_INTERVAL.get()))) {
      cue(player, state, features, RING, null);
      for (var enemy : nearby(player, Math.min(5, AkatsukiDomainConfig.RADIUS.get())))
        AkatsukiDomainDamage.pulse(player, enemy, state, has(features, "adaptation"));
    }
    if (has(features, "tracking") && state.due("tracking", now, 20)) {
      if (target == null) {
        var targets = nearby(player, AkatsukiDomainConfig.RADIUS.get());
        if (!targets.isEmpty()) {
          target = targets.get(0);
          state.target = target.getUUID();
        }
      }
      if (target != null) cue(player, state, features, LOCK, target);
    }
    if (has(features, "counter")
        && state.due("counter", now, AkatsukiDomainConfig.COUNTER_INTERVAL.get()))
      counter(player, state, features);
    var chain = CHAINS.remove(player);
    if (has(features, "chain") && chain != null && now <= chain.expires()) {
      chaining = true;
      try {
        for (var enemy :
            nearby(player, AkatsukiDomainConfig.RADIUS.get()).stream()
                .filter(t -> t.position().distanceToSqr(chain.center()) <= 16)
                .limit(3)
                .toList()) {
          cue(player, state, features, CHAIN, enemy);
          AkatsukiDomainDamage.pulse(player, enemy, state, has(features, "adaptation"));
        }
      } finally {
        chaining = false;
      }
    }
    if (has(features, "rain")
        && state.due("rain", now, interval(state, AkatsukiDomainConfig.RAIN_INTERVAL.get()))) {
      cue(player, state, features, RAIN, target);
      for (var enemy : nearby(player, AkatsukiDomainConfig.RADIUS.get()))
        AkatsukiDomainDamage.pulse(player, enemy, state, has(features, "adaptation"));
    }
    if (has(features, "complete")
        && target != null
        && state.due("complete", now, state.lifecycle.rampage() ? 40 : 80)) {
      cue(player, state, features, FINAL, target);
      // One focused pulse; the client unfolds dozens of converging cuts independently.
      if (target != null)
        AkatsukiDomainDamage.pulse(player, target, state, has(features, "adaptation"));
    }
  }

  private static int interval(AkatsukiDomainSession state, int normal) {
    return state.lifecycle.rampage() ? Math.max(10, normal / 2) : normal;
  }

  private static boolean validTarget(ServerPlayer player, LivingEntity target) {
    double radius = AkatsukiDomainConfig.RADIUS.get();
    return combatTarget(player, target, SESSIONS.get(player))
        && player.distanceToSqr(target) <= radius * radius
        && player.hasLineOfSight(target);
  }

  private static List<LivingEntity> nearby(ServerPlayer player, double radius) {
    return player
        .level()
        .getEntitiesOfClass(
            LivingEntity.class,
            player.getBoundingBox().inflate(radius),
            t -> hostile(player, t)
                && validTarget(player, t)
                && player.distanceToSqr(t) <= radius * radius)
        .stream()
        .sorted(Comparator.comparingDouble(player::distanceToSqr))
        .limit(AkatsukiDomainConfig.MAX_TARGETS.get())
        .toList();
  }

  private static void counter(
      ServerPlayer player, AkatsukiDomainSession state, ContractAbilityFeatureSet features) {
    var shots =
        player
            .level()
            .getEntitiesOfClass(
                Projectile.class,
                player.getBoundingBox().inflate(4),
                shot ->
                    (shot.getType() == EntityType.ARROW
                            || shot.getType() == EntityType.SPECTRAL_ARROW
                            || shot.getType() == EntityType.SMALL_FIREBALL)
                        && shot.getOwner() instanceof LivingEntity owner
                        && hostile(player, owner)
                        && shot.getDeltaMovement()
                                .dot(player.getEyePosition().subtract(shot.position()))
                            > 0
                        && player.hasLineOfSight(shot));
    if (shots.isEmpty()) return;
    var shot = shots.get(0);
    var owner = (LivingEntity) shot.getOwner();
    shot.discard(); // Explicit vanilla whitelist, one projectile per interval; no boss projectile
    // removal.
    cue(player, state, features, COUNTER, owner);
    if (validTarget(player, owner))
      AkatsukiDomainDamage.pulse(player, owner, state, has(features, "adaptation"));
  }

  /**
   * Runs after armor/absorption, before vanilla's totem check. A successful rescue consumes no
   * totem.
   */
  @SubscribeEvent(priority = EventPriority.LOWEST)
  public static void rescueDamage(LivingDamageEvent e) {
    if (!(e.getEntity() instanceof ServerPlayer p)
        || e.getAmount() <= 0
        || !rescuable(e.getSource())) return;
    var f = AkatsukiDomainGrowth.capabilities(p);
    if (!has(f, "cloak")) return;
    var s = refresh(p, f);
    if (e.getAmount() >= p.getHealth()
        || p.getHealth() - e.getAmount()
            <= p.getMaxHealth() * AkatsukiDomainConfig.RESCUE_THRESHOLD.get()) {
      if (rescue(p, s, f)) e.setCanceled(true);
    }
  }

  /** Short finite damage protection also covers the collapse/reignite window. */
  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public static void protection(LivingAttackEvent e) {
    if (e.getEntity() instanceof ServerPlayer p && rescuable(e.getSource())) {
      var s = SESSIONS.get(p);
      if (s != null && now(p) < s.protectionUntil && !p.isCreative() && !p.isSpectator())
        e.setCanceled(true);
    }
  }

  static boolean rescuable(net.minecraft.world.damagesource.DamageSource source) {
    return !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)
        && !source.is(net.minecraft.world.damagesource.DamageTypes.FELL_OUT_OF_WORLD)
        && !source.is(net.minecraft.world.damagesource.DamageTypes.GENERIC_KILL);
  }

  private static boolean rescue(
      ServerPlayer p, AkatsukiDomainSession s, ContractAbilityFeatureSet f) {
    if (!AkatsukiDomainConfig.RESCUE.get()
        || !has(f, "cloak")
        || !s.attacking()
        || !p.isAlive()
        || p.isCreative()
        || p.isSpectator()
        || !AkatsukiDomainAuthorization.allows(p, p.getMainHandItem())) return false;
    long time = now(p);
    if (!AkatsukiRescueSavedData.get(p.getServer())
        .claim(p.getUUID(), time, AkatsukiDomainConfig.RESCUE_COOLDOWN.get())) return false;
    int collapse = AkatsukiDomainConfig.COLLAPSE_TICKS.get();
    int reignite = AkatsukiDomainConfig.REIGNITE_TICKS.get();
    s.lifecycle.rescue(time, collapse, reignite);
    s.protectionUntil =
        time + Math.max(collapse + reignite + 2, AkatsukiDomainConfig.PROTECTION_TICKS.get());
    p.setHealth(
        (float)
            Math.min(
                p.getMaxHealth(),
                Math.max(.01, p.getMaxHealth() * AkatsukiDomainConfig.RESCUE_HEALTH.get())));
    CHAINS.remove(p);
    cue(p, s, f, COLLAPSE, null);
    cue(p, s, f, STATE, null);
    return true;
  }

  private static void cue(
      ServerPlayer player,
      AkatsukiDomainSession state,
      ContractAbilityFeatureSet f,
      int kind,
      LivingEntity target) {
    AkatsukiDomainNetwork.send(
        player,
        kind,
        target == null ? player.position() : target.getBoundingBox().getCenter(),
        AkatsukiDomainConfig.RADIUS.get().floatValue(),
        f,
        15,
        state.lifecycle.phase(),
        target == null ? -1 : target.getId());
  }

  private static void stop(ServerPlayer player, AkatsukiDomainSession state) {
    CHAINS.remove(player);
    if (state.active()) {
      AkatsukiDomainNetwork.send(
          player,
          END,
          player.position(),
          0,
          ContractAbilityFeatureSet.EMPTY,
          0,
          AkatsukiDomainState.Phase.INACTIVE,
          -1);
    }
    state.suspend();
  }

  @SubscribeEvent
  public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
    if (e.getEntity() instanceof ServerPlayer p) remove(p);
  }

  @SubscribeEvent
  public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) {
    if (e.getEntity() instanceof ServerPlayer p) remove(p);
  }

  @SubscribeEvent(priority = EventPriority.LOWEST)
  public static void death(LivingDeathEvent e) {
    if (e.getEntity() instanceof ServerPlayer p) {
      remove(p);
      return;
    }
    AkatsukiDomainDamage.forget(e.getEntity());
    if (chaining || !(e.getSource().getEntity() instanceof ServerPlayer p)) return;
    var state = SESSIONS.get(p);
    if (state == null
        || !state.attacking()
        || !has(AkatsukiDomainGrowth.capabilities(p), "chain")
        || p.distanceToSqr(e.getEntity())
            > AkatsukiDomainConfig.RADIUS.get() * AkatsukiDomainConfig.RADIUS.get()
        || !(e.getEntity() instanceof Enemy)
        || e.getEntity().isAlliedTo(p)
        || !AkatsukiDomainDamage.domain(e.getSource())
            && (AkatsukiCombatCompat.secondary(e.getSource())
                || ContractCombatCredits.resolveHit(e.getSource()) == null)
        || !state.due("chain", now(p), AkatsukiDomainConfig.CHAIN_INTERVAL.get())) return;
    CHAINS.put(p, new Chain(e.getEntity().position(), now(p) + 10));
  }

  @SubscribeEvent
  public static void serverTick(TickEvent.ServerTickEvent e) {
    if (e.phase == TickEvent.Phase.END && e.getServer().getTickCount() % 40 == 0) {
      long now = e.getServer().overworld().getGameTime();
      AkatsukiDomainDamage.prune(now);
    }
  }

  @SubscribeEvent
  public static void leave(net.minecraftforge.event.entity.EntityLeaveLevelEvent e) {
    if (!e.getLevel().isClientSide() && e.getEntity() instanceof LivingEntity living)
      AkatsukiDomainDamage.forget(living);
  }

  private static void remove(ServerPlayer p) {
    AkatsukiDomainAuthorization.clear(p);
    CHAINS.remove(p);
    var s = SESSIONS.remove(p);
    if (s != null) stop(p, s);
  }

  @SubscribeEvent
  public static void stopped(ServerStoppedEvent e) {
    SESSIONS.clear();
    CHAINS.clear();
    AkatsukiDomainAuthorization.clear();
    AkatsukiDomainDamage.clear();
  }

  private AkatsukiDomainService() {}
}
