package com.maidweapon.forge.system.akatsuki.soul;

import com.maidweapon.common.SoulDamageConfig;
import com.maidweapon.forge.entity.SoulAnchorEntity;
import com.maidweapon.forge.init.ModSoulEntities;
import com.maidweapon.forge.system.akatsuki.*;
import com.maidweapon.forge.system.akatsuki.provenance.*;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

/**
 * One authority for owner-directed melee and launch-authenticated native
 * SlashBlade summoned-sword collisions. Raw DamageSource never activates cores.
 */
@Mod.EventBusSubscriber(modid = "maid_weapon")
public final class SoulAnchorService {
  private record Key(UUID owner, UUID target) {}

  private static final Map<Key, SoulAnchorEntity> ANCHORS = new HashMap<>();
  private static final Map<Key, Long> NEXT = new HashMap<>();
  private static final Map<UUID, Long> LAST_HIT = new HashMap<>(), HOVER = new HashMap<>();

  @SubscribeEvent(priority = EventPriority.LOWEST)
  public static void observed(DamageObservedEvent e) {
    boolean issued = DamageObservationService.issued(e.observation, e.target);
    if (diagnostics())
      com.mojang.logging.LogUtils.getLogger().info(
          "SOUL_ANCHOR_OBSERVATION owner={} target={} classification={} recoverable={} issued={} effectiveHP={} actualHP={} lossHP={}",
          e.observation.player(), e.observation.target(), e.observation.classification(),
          e.observation.recoverable(), issued, e.observation.effective(),
          e.observation.actual(), e.observation.loss());
    if (SoulAnchorTriggerPolicy.eligible(issued,
            e.observation.proof() != null && e.observation.proof().valid()
                && e.observation.proof().currentCarrier(),
            e.observation.raw(), e.observation.classification().name()))
      create(e.observation.proof().player(), e.target);
  }

  private static boolean diagnostics() {
    return SoulDamageConfig.DIAGNOSTICS.get()
        || Boolean.getBoolean("contractblade.soul.validation")
        || Boolean.getBoolean("contractblade.soulVisual.validation");
  }

  private static SoulAnchorEntity rejected(ServerPlayer p, LivingEntity t, String reason) {
    if (diagnostics())
      com.mojang.logging.LogUtils.getLogger().info(
          "SOUL_ANCHOR_REJECTED owner={} target={} targetType={} reason={}",
          p.getUUID(), t.getUUID(),
          net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(t.getType()), reason);
    return null;
  }

  public static SoulAnchorEntity create(ServerPlayer p, LivingEntity t) {
    var s = AkatsukiDomainService.session(p);
    if (!SoulAnchorBinding.unlocked(p))
      return rejected(p, t, "ability_locked_or_spirit_not_resident");
    if (!SoulDamageEngine.authorized(p, t, s))
      return rejected(p, t, "domain_or_target_not_authorized");
    var targetState = com.maidweapon.forge.compat.DomainTargetCompatibility.inspect(t);
    if (targetState.protectedPhase()
        || com.maidweapon.forge.compat.DomainTargetCompatibility.initializing(t))
      return rejected(p, t, "protected_phase_or_initializing");
    ANCHORS
        .values()
        .removeIf(
            c -> {
              if (!SoulAnchorBinding.valid(c)) c.discard();
              return c.isRemoved();
            });
    var key = new Key(p.getUUID(), t.getUUID());
    var existing = ANCHORS.get(key);
    if (existing != null) {
      if (diagnostics())
        com.mojang.logging.LogUtils.getLogger().info(
            "SOUL_ANCHOR_REUSED owner={} target={} core={}", p.getUUID(), t.getUUID(), existing.getId());
      return existing;
    }
    long now = SoulDamageEngine.now(p);
    if (now < NEXT.getOrDefault(key, Long.MIN_VALUE))
      return rejected(p, t, "creation_cooldown");
    if (ANCHORS.size() >= 256) return rejected(p, t, "global_anchor_limit");
    if (ANCHORS.keySet().stream().filter(k -> k.owner.equals(p.getUUID())).count()
            >= SoulDamageConfig.OWNER_ANCHORS.get())
      return rejected(p, t, "owner_anchor_limit");
    var point = SoulAnchorPlacement.find(t);
    if (point == null) return rejected(p, t, "no_collision_free_position");
    var c = ModSoulEntities.ANCHOR.get().create(p.serverLevel());
    if (c == null) return rejected(p, t, "entity_creation_failed");
    c.owner(p.getUUID());
    c.original = t.getUUID();
    c.binding = s.binding;
    c.targetPhase = targetState.phase();
    c.expires =
        now
            + SoulDamageConfig.ANCHOR_LIFETIME.get()
            + (long) AkatsukiDomainGrowth.parameter(p, "anchor_lifetime_bonus");
    c.targetId(t.getId());
    c.moveTo(point.x, point.y, point.z, 0, 0);
    if (!p.serverLevel().addFreshEntity(c)) return rejected(p, t, "world_rejected_entity");
    ANCHORS.put(key, c);
    NEXT.put(key, now + SoulDamageConfig.ANCHOR_INTERVAL.get());
    if (diagnostics())
      com.mojang.logging.LogUtils.getLogger().info(
          "SOUL_ANCHOR_CREATED owner={} target={} core={} dimension={} x={} y={} z={} lifetimeTicks={}",
          p.getUUID(), t.getUUID(), c.getId(), p.level().dimension().location(),
          c.getX(), c.getY(), c.getZ(), c.expires - now);
    return c;
  }

  public static boolean valid(SoulAnchorEntity c) {
    return SoulAnchorBinding.valid(c);
  }

  public static void fixtureOwner(ServerPlayer p) {
    SoulAnchorBinding.fixtureOwner(p);
  }

  public static void follow(SoulAnchorEntity c) {
    if (!valid(c)) {
      if (diagnostics())
        com.mojang.logging.LogUtils.getLogger().info(
            "SOUL_ANCHOR_REMOVED core={} owner={} target={} reason=expired_or_authority_target_debt_invalid",
            c.getId(), c.owner, c.original);
      c.discard();
      return;
    }
    var t = c.level().getEntity(c.targetId());
    if (!(t instanceof LivingEntity living)) {
      c.discard();
      return;
    }
    var point = SoulAnchorPlacement.find(living);
    if (point == null) {
      if (diagnostics())
        com.mojang.logging.LogUtils.getLogger().info(
            "SOUL_ANCHOR_REMOVED core={} reason=no_collision_free_follow_position", c.getId());
      c.discard();
    } else {
      c.setPos(point);
      // Fallback for low graphics presets, shaders and resource packs that suppress
      // the translucent shard renderer. Only the owner receives these particles.
      if (c.tickCount % 5 == 0) {
        var owner = SoulAnchorBinding.owner(c);
        if (owner != null && owner.distanceToSqr(c) <= 4096)
          owner.serverLevel().sendParticles(
              owner, net.minecraft.core.particles.ParticleTypes.END_ROD, true,
              c.getX(), c.getY() + .85, c.getZ(),
              2, .18, .27, .18, .008);
      }
    }
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
  public static void attack(AttackEntityEvent e) {
    if (!(e.getTarget() instanceof SoulAnchorEntity c)) return;
    e.setCanceled(true);
    if (!(e.getEntity() instanceof ServerPlayer p)) return;
    if (!precise(p, c)) {
      if (diagnostics())
        com.mojang.logging.LogUtils.getLogger().info(
            "SOUL_ANCHOR_CLICK_REJECTED owner={} core={} reason=ownership_validity_airborne_aim_reach_or_occlusion",
            p.getUUID(), c.getId());
      return;
    }
    if (activate(p, c)) {
      p.resetAttackStrengthTicker();
      p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
      long hover = (long) AkatsukiDomainGrowth.parameter(p, "aerial_hover_ticks");
      if (hover > 0) HOVER.put(p.getUUID(), SoulDamageEngine.now(p) + hover);
      if (diagnostics())
        com.mojang.logging.LogUtils.getLogger().info(
            "SOUL_DIRECT_CLICK_ACCEPTED owner={} core={} feetY={}",
            p.getUUID(), c.getId(), p.getY());
    } else if (diagnostics()) {
      com.mojang.logging.LogUtils.getLogger().info(
          "SOUL_ANCHOR_CLICK_REJECTED owner={} core={} reason=burst_cooldown_running_or_no_debt",
          p.getUUID(), c.getId());
    }
  }

  /** Called only after native projectile-launch provenance and swept LOS validation. */
  static boolean projectileHit(ServerPlayer p, SoulAnchorEntity c) {
    return p.getUUID().equals(c.owner) && valid(c) && activate(p, c);
  }

  /** Shared single-activation gate; projectile hits never reset melee swing timing. */
  private static boolean activate(ServerPlayer p, SoulAnchorEntity c) {
    long now = SoulDamageEngine.now(p);
    if (LAST_HIT.getOrDefault(c.getUUID(), Long.MIN_VALUE) == now) return false;
    if (!SoulBurstService.strike(p, c)) return false;
    LAST_HIT.put(c.getUUID(), now);
    c.shake();
    return true;
  }

  /** Active cores only, not a whole-world entity scan. */
  static java.util.List<SoulAnchorEntity> inLevel(net.minecraft.server.level.ServerLevel level) {
    return ANCHORS.values().stream()
        .filter(c -> !c.isRemoved() && c.level() == level).toList();
  }

  private static boolean precise(ServerPlayer p, SoulAnchorEntity c) {
    if (!p.getUUID().equals(c.owner)
        || !valid(c)
        || p.onGround()
        || p.getEyeY() < c.getY() - .8
        || p.getAttackStrengthScale(.5f) < .2f) return false;
    double reach =
        Math.min(
            8,
            Math.max(0, p.getAttributeValue(net.minecraftforge.common.ForgeMod.ENTITY_REACH.get()))
                + .3);
    var eye = p.getEyePosition();
    var end = eye.add(p.getLookAngle().scale(reach));
    var hit =
        c.getBoundingBox()
            .inflate(AkatsukiDomainGrowth.parameter(p, "precision_tolerance"))
            .clip(eye, end);
    if (hit.isEmpty()) return false;
    double distance = eye.distanceToSqr(hit.get());
    var block =
        p.level()
            .clip(
                new ClipContext(
                    eye, hit.get(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
    if (block.getType() != HitResult.Type.MISS
        && eye.distanceToSqr(block.getLocation()) + 1e-5 < distance) return false;
    for (var other :
        p.level()
            .getEntities(
                p,
                p.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1),
                x -> x != c && x.isPickable() && !(x instanceof SoulAnchorEntity))) {
      var occlusion = other.getBoundingBox().clip(eye, hit.get());
      if (occlusion.isPresent() && eye.distanceToSqr(occlusion.get()) + 1e-5 < distance)
        return false;
    }
    return true;
  }

  @SubscribeEvent
  public static void playerTick(TickEvent.PlayerTickEvent e) {
    if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
    var until = HOVER.get(p.getUUID());
    if (until != null) {
      if (SoulDamageEngine.now(p) >= until || p.onGround() || !SoulAnchorBinding.unlocked(p))
        HOVER.remove(p.getUUID());
      else if (p.getDeltaMovement().y < 0) {
        p.setDeltaMovement(p.getDeltaMovement().multiply(1, .15, 1));
        p.hurtMarked = true;
      }
    }
  }

  @SubscribeEvent
  public static void prune(TickEvent.ServerTickEvent e) {
    if (e.phase != TickEvent.Phase.END || e.getServer().getTickCount() % 20 != 0) return;
    long now = e.getServer().overworld().getGameTime();
    ANCHORS
        .values()
        .removeIf(
            c -> {
              if (!valid(c)) c.discard();
              return c.isRemoved();
            });
    HOVER.values().removeIf(t -> t <= now);
    NEXT.values().removeIf(t -> t < now - 200);
    LAST_HIT.values().removeIf(t -> t < now - 60);
  }

  @SubscribeEvent
  public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
    UUID id = e.getEntity().getUUID();
    ANCHORS
        .values()
        .removeIf(
            c -> {
              if (id.equals(c.owner)) c.discard();
              return c.isRemoved();
            });
    HOVER.remove(id);
  }

  @SubscribeEvent
  public static void stop(ServerStoppedEvent e) {
    ANCHORS.clear();
    NEXT.clear();
    LAST_HIT.clear();
    HOVER.clear();
    SoulAnchorBinding.clear();
    SoulBurstService.clear();
  }

  private SoulAnchorService() {}
}
