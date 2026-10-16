package com.maidweapon.forge.system.akatsuki.soul;

import com.maidweapon.common.SoulDamageConfig;
import com.maidweapon.forge.compat.AkatsukiCombatCompat;
import com.maidweapon.forge.entity.SoulAnchorEntity;
import com.maidweapon.forge.init.ModSoulEntities;
import com.maidweapon.forge.system.akatsuki.*;
import com.maidweapon.forge.system.mastery.ContractCombatCredits;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

/** Server-owned binding and actual-health hit confirmation. A visual contact cannot mint a soul. */
@Mod.EventBusSubscriber(modid = "maid_weapon")
public final class SoulAnchorService {
  private record Key(UUID owner, UUID target) {}

  private record Pending(ServerPlayer owner, LivingEntity target, float before, long tick) {}

  private static final Map<Key, SoulAnchorEntity> ANCHORS = new HashMap<>();
  private static final Map<Key, Long> NEXT = new HashMap<>();
  private static final Map<Key, Pending> PENDING = new HashMap<>();
  private static final Map<UUID, Long> LAST_HIT = new HashMap<>();

  private static boolean unlocked(ServerPlayer p) {
    return !p.isSpectator()
        && AkatsukiDomainGrowth.has(AkatsukiDomainGrowth.capabilities(p), "swords")
        && AkatsukiDomainAuthorization.allows(p, p.getMainHandItem());
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public static void observe(LivingAttackEvent event) {
    observe(event.getEntity(), event.getSource(), event.getAmount());
  }

  private static void observe(LivingEntity target, DamageSource source, float amount) {
    if (amount <= 0
        || AkatsukiDomainDamage.domain(source)
        || AkatsukiCombatCompat.secondary(source)
        || source
            .typeHolder()
            .unwrapKey()
            .map(k -> k.location().toString().equals("blade_tetra:akatsuki_execution"))
            .orElse(false)) return;
    var hit = ContractCombatCredits.resolveHit(source);
    if (hit == null || hit.carrier() != hit.player().getMainHandItem() || !unlocked(hit.player()))
      return;
    var p = hit.player();
    var s = AkatsukiDomainService.session(p);
    if (!SoulDamageEngine.authorized(p, target, s) || p.distanceToSqr(target) > 1024) return;
    var key = new Key(p.getUUID(), target.getUUID());
    if (PENDING.size() < 256)
      PENDING.putIfAbsent(key, new Pending(p, target, target.getHealth(), SoulDamageEngine.now(p)));
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public static void confirm(TickEvent.PlayerTickEvent event) {
    if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) return;
    long now = SoulDamageEngine.now(p);
    var ready = new ArrayList<Pending>();
    PENDING
        .entrySet()
        .removeIf(
            e -> {
              var pending = e.getValue();
              if (pending.owner == p) {
                ready.add(pending);
                return true;
              }
              return now - pending.tick > 2 || pending.owner.isRemoved();
            });
    for (var hit : ready)
      if (hit.target.isAlive()
          && !hit.target.isRemoved()
          && hit.target.getHealth() < hit.before
          && unlocked(p)) create(p, hit.target);
  }

  /** Invoked only after a real native HP loss; also used by the opt-in runtime fixture. */
  public static SoulAnchorEntity create(ServerPlayer p, LivingEntity target) {
    var s = AkatsukiDomainService.session(p);
    if (!unlocked(p) || !SoulDamageEngine.authorized(p, target, s)) return null;
    ANCHORS.entrySet().removeIf(entry -> entry.getValue().isRemoved());
    long now = SoulDamageEngine.now(p);
    var key = new Key(p.getUUID(), target.getUUID());
    var existing = ANCHORS.get(key);
    if (existing != null && !existing.isRemoved()) return existing;
    if (now < NEXT.getOrDefault(key, Long.MIN_VALUE)
        || ANCHORS.size() >= 256
        || ANCHORS.keySet().stream().filter(k -> k.owner.equals(p.getUUID())).count()
            >= SoulDamageConfig.OWNER_ANCHORS.get()) return null;
    var core = ModSoulEntities.ANCHOR.get().create(p.serverLevel());
    if (core == null) return null;
    core.owner(p.getUUID());
    core.original = target.getUUID();
    core.binding = s.binding;
    core.expires = now + SoulDamageConfig.ANCHOR_LIFETIME.get();
    core.targetId(target.getId());
    var direction = p.position().subtract(target.position()).multiply(1, 0, 1).normalize();
    double distance = Math.min(p.distanceTo(target) * .5, target.getBbWidth() * .5 + 1.3);
    var point =
        target
            .position()
            .add(direction.scale(distance))
            .add(direction.cross(new net.minecraft.world.phys.Vec3(0, 1, 0)).scale(.85));
    core.moveTo(point.x, target.getY() + .25, point.z, 0, 0);
    if (!p.serverLevel().addFreshEntity(core)) return null;
    ANCHORS.put(key, core);
    NEXT.put(key, now + SoulDamageConfig.ANCHOR_INTERVAL.get());
    return core;
  }

  public static boolean valid(SoulAnchorEntity core) {
    if (core.owner == null || core.original == null || core.isRemoved()) return false;
    var p = core.getServer().getPlayerList().getPlayer(core.owner);
    // Fake players are not in PlayerList; runtime fixtures explicitly register their owner below.
    if (p == null) p = FIXTURE_OWNERS.get(core.owner);
    if (p == null
        || p.level() != core.level()
        || !p.isAlive()
        || !unlocked(p)
        || SoulDamageEngine.now(p) >= core.expires) return false;
    var s = AkatsukiDomainService.session(p);
    var target = p.serverLevel().getEntity(core.original);
    return core.binding.equals(s.binding)
        && target instanceof LivingEntity t
        && t.getId() == core.targetId()
        && SoulDamageEngine.authorized(p, t, s)
        && core.distanceToSqr(t)
            <= SoulDamageConfig.LINK_RANGE.get() * SoulDamageConfig.LINK_RANGE.get();
  }

  private static final Map<UUID, ServerPlayer> FIXTURE_OWNERS = new HashMap<>();

  public static void fixtureOwner(ServerPlayer p) {
    if (Boolean.getBoolean("contractblade.soul.validation")) FIXTURE_OWNERS.put(p.getUUID(), p);
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public static void attack(AttackEntityEvent e) {
    if (!(e.getTarget() instanceof SoulAnchorEntity core)) return;
    e.setCanceled(true);
    if (e.getEntity() instanceof ServerPlayer p
        && p.getAttackStrengthScale(.5f) >= .2f
        && strike(core, p.damageSources().playerAttack(p))) {
      p.resetAttackStrengthTicker();
      p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
    }
  }

  public static boolean strike(SoulAnchorEntity core, DamageSource source) {
    if (AkatsukiDomainDamage.domain(source)
        || AkatsukiCombatCompat.secondary(source)
        || !(source.getEntity() instanceof ServerPlayer p)
        || !p.getUUID().equals(core.owner)
        || !valid(core)) return false;
    var credit = ContractCombatCredits.resolveHit(source);
    if (credit == null || credit.player() != p || credit.carrier() != p.getMainHandItem())
      return false;
    double reach = p.getAttributeValue(net.minecraftforge.common.ForgeMod.ENTITY_REACH.get()) + .3;
    if (core.getBoundingBox().distanceToSqr(p.getEyePosition()) > reach * reach
        || !p.hasLineOfSight(core)) return false;
    long now = SoulDamageEngine.now(p);
    if (LAST_HIT.getOrDefault(core.getUUID(), Long.MIN_VALUE) == now) return false;
    LAST_HIT.put(core.getUUID(), now);
    boolean accepted = SoulBurstService.strike(p, core);
    if (accepted) core.shake();
    return accepted;
  }

  @SubscribeEvent
  public static void prune(TickEvent.ServerTickEvent e) {
    if (e.phase != TickEvent.Phase.END || e.getServer().getTickCount() % 20 != 0) return;
    long now = e.getServer().overworld().getGameTime();
    ANCHORS.entrySet().removeIf(entry -> entry.getValue().isRemoved());
    NEXT.entrySet().removeIf(entry -> entry.getValue() < now - 200);
    LAST_HIT.entrySet().removeIf(entry -> entry.getValue() < now - 60);
  }

  @SubscribeEvent
  public static void stop(ServerStoppedEvent e) {
    ANCHORS.clear();
    NEXT.clear();
    PENDING.clear();
    LAST_HIT.clear();
    FIXTURE_OWNERS.clear();
    SoulBurstService.clear();
  }

  private SoulAnchorService() {}
}
