package com.maidweapon.forge.system.akatsuki.soul;

import com.maidweapon.common.SoulDamageConfig;
import com.maidweapon.forge.entity.SoulAnchorEntity;
import com.maidweapon.forge.init.ModSoulEntities;
import com.maidweapon.forge.system.akatsuki.*;
import com.maidweapon.forge.system.akatsuki.provenance.*;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

/**
 * Only an owner-directed native attack packet can activate an aerial core. DamageSource is
 * insufficient.
 */
@Mod.EventBusSubscriber(modid = "maid_weapon")
public final class SoulAnchorService {
  private record Key(UUID owner, UUID target) {}

  private static final Map<Key, SoulAnchorEntity> ANCHORS = new HashMap<>();
  private static final Map<Key, Long> NEXT = new HashMap<>();
  private static final Map<UUID, Long> LAST_HIT = new HashMap<>(), HOVER = new HashMap<>();

  @SubscribeEvent(priority = EventPriority.LOWEST)
  public static void observed(DamageObservedEvent e) {
    if (e.observation.recoverable() && DamageObservationService.issued(e.observation, e.target))
      create(e.observation.proof().player(), e.target);
  }

  public static SoulAnchorEntity create(ServerPlayer p, LivingEntity t) {
    var s = AkatsukiDomainService.session(p);
    if (!SoulAnchorBinding.unlocked(p)
        || !SoulDamageEngine.authorized(p, t, s)
        || SoulDamageDebtService.available(p, t, true) <= .01) return null;
    ANCHORS
        .values()
        .removeIf(
            c -> {
              if (!SoulAnchorBinding.valid(c)) c.discard();
              return c.isRemoved();
            });
    var key = new Key(p.getUUID(), t.getUUID());
    var existing = ANCHORS.get(key);
    if (existing != null) return existing;
    long now = SoulDamageEngine.now(p);
    if (now < NEXT.getOrDefault(key, Long.MIN_VALUE)
        || ANCHORS.size() >= 256
        || ANCHORS.keySet().stream().filter(k -> k.owner.equals(p.getUUID())).count()
            >= SoulDamageConfig.OWNER_ANCHORS.get()) return null;
    var point = SoulAnchorPlacement.find(t);
    if (point == null) return null;
    var c = ModSoulEntities.ANCHOR.get().create(p.serverLevel());
    if (c == null) return null;
    c.owner(p.getUUID());
    c.original = t.getUUID();
    c.binding = s.binding;
    c.expires =
        now
            + SoulDamageConfig.ANCHOR_LIFETIME.get()
            + (long) AkatsukiDomainGrowth.parameter(p, "anchor_lifetime_bonus");
    c.targetId(t.getId());
    c.moveTo(point.x, point.y, point.z, 0, 0);
    if (!p.serverLevel().addFreshEntity(c)) return null;
    ANCHORS.put(key, c);
    NEXT.put(key, now + SoulDamageConfig.ANCHOR_INTERVAL.get());
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
      c.discard();
      return;
    }
    var t = c.level().getEntity(c.targetId());
    if (!(t instanceof LivingEntity living)) {
      c.discard();
      return;
    }
    var point = SoulAnchorPlacement.find(living);
    if (point == null) c.discard();
    else c.setPos(point);
  }

  @Deprecated
  public static boolean strike(SoulAnchorEntity c, DamageSource source) {
    return false;
  }

  /** Compatibility no-op: old tick-delta confirmation deliberately cannot mint a receipt. */
  @Deprecated
  public static void confirm(TickEvent.PlayerTickEvent e) {}

  @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
  public static void attack(AttackEntityEvent e) {
    if (!(e.getTarget() instanceof SoulAnchorEntity c)) return;
    e.setCanceled(true);
    if (!(e.getEntity() instanceof ServerPlayer p) || !precise(p, c)) return;
    long now = SoulDamageEngine.now(p);
    if (LAST_HIT.getOrDefault(c.getUUID(), Long.MIN_VALUE) == now) return;
    LAST_HIT.put(c.getUUID(), now);
    if (SoulBurstService.strike(p, c)) {
      c.shake();
      p.resetAttackStrengthTicker();
      p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
      long hover = (long) AkatsukiDomainGrowth.parameter(p, "aerial_hover_ticks");
      if (hover > 0) HOVER.put(p.getUUID(), now + hover);
      if (Boolean.getBoolean("contractblade.soul.validation")
          || Boolean.getBoolean("contractblade.soulVisual.validation"))
        com.mojang.logging.LogUtils.getLogger()
            .info(
                "SOUL_DIRECT_CLICK_ACCEPTED owner={} core={} feetY={}",
                p.getUUID(),
                c.getId(),
                p.getY());
    }
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
