package com.maidweapon.forge.system.akatsuki;

import com.maidweapon.common.SoulDamageConfig;
import com.maidweapon.forge.compat.DomainTargetCompatibility;
import com.maidweapon.forge.entity.SoulAnchorEntity;
import com.maidweapon.forge.system.akatsuki.provenance.*;
import com.maidweapon.forge.system.akatsuki.soul.*;
import com.maidweapon.forge.system.mastery.*;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

/** Native Forge hurt/attack fixture. FakePlayer interaction is not claimed as human gameplay. */
@Mod.EventBusSubscriber(modid = "maid_weapon")
public final class SoulProvenanceValidation {
  private static LivingEntity reduced, canceled, nested, throwing;
  private static ServerPlayer foreign;
  private static boolean installed;

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public static void reduce(LivingHurtEvent e) {
    if (e.getEntity() == reduced) e.setAmount(.25f);
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public static void cancel(LivingAttackEvent e) {
    if (e.getEntity() == canceled) e.setCanceled(true);
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public static void nested(LivingHurtEvent e) {
    if (e.getEntity() == throwing)
      throw new IllegalStateException("EXPECTED_OBSERVATION_EXCEPTION");
    if (e.getEntity() == nested) {
      var t = nested;
      nested = null;
      t.invulnerableTime = 0;
      t.hurt(foreign.damageSources().generic(), 2);
      e.setCanceled(true);
    }
  }

  private static synchronized void install() {
    if (installed) return;
    if (!Boolean.getBoolean("contractblade.soul.validation")
        && !Boolean.getBoolean("contractblade.soulVisual.validation"))
      throw new IllegalStateException("Fixture outside opt-in run");
    installed = true;
    DomainTargetCompatibility.register(
        "maid_weapon:provenance_fixture",
        new DomainTargetCompatibility.Adapter() {
          public boolean supports(LivingEntity t) {
            return t instanceof Capped;
          }

          public DomainTargetCompatibility.State inspect(LivingEntity t) {
            var c = (Capped) t;
            return new DomainTargetCompatibility.State(
                5, 10, c.protectedPhase, "fixture:" + c.phase);
          }
        });
  }

  public static LivingEntity visualTarget(ServerLevel l) {
    install();
    return cap(l);
  }

  public static void run(MinecraftServer server) throws Exception {
    install();
    nativeInvocationProof(server);
    var l = server.overworld();
    var p = player(l, "ProvenanceOwner");
    foreign = player(l, "ProvenanceForeign");
    nativeSlashProof(p);
    for (int level = 1; level <= 10; level++) {
      setLevel(p, level);
      advance(l, 240);
      var t = cap(l);
      float before = t.getHealth();
      nativeHit(t, p, 100);
      var o = DamageObservationService.latest(server);
      check(o.recoverable() && near(o.effective(), 100) && near(o.actual(), 5), "E100 A5 evidence");
      var s = AkatsukiDomainService.session(p);
      s.contact(l.getGameTime(), t.getUUID());
      var cut = SoulDamageEngine.cut(p, t, s);
      check(
          cut != null && near(cut.loss(), 95 * SoulRecoveryPolicy.cut(level)),
          "ordinary level " + level + " feedback=" + cut);
      double ordinary = cut.loss(), air = 0;
      if (level >= 2) {
        var core = SoulAnchorService.create(p, t);
        check(core != null && core.getY() >= t.getBoundingBox().maxY + 4, "high aerial placement");
        check(!core.hurt(p.damageSources().playerAttack(p), 100), "damage cannot activate core");
        float hp = t.getHealth();
        aim(p, core);
        p.attack(core);
        for (int i = 0; i < 14; i++) {
          advance(l, 1);
          SoulBurstService.tick(
              new TickEvent.ServerTickEvent(TickEvent.Phase.END, () -> true, server));
        }
        air = hp - t.getHealth();
        check(
            near(ordinary + air, 95 * SoulRecoveryPolicy.aerial(level)),
            "cumulative aerial level " + level);
        check(core.isRemoved(), "core final fracture");
      }
      check(before - t.getHealth() <= 100.025, "never native plus 100 percent again");
      LogUtils.getLogger()
          .info(
              "SOUL_RECOVERY_LEVEL level={} E={} A={} L={} ordinary={} aerialAdditional={}"
                  + " cumulativeHP={} before={} after={}",
              level,
              o.effective(),
              o.actual(),
              o.loss(),
              ordinary,
              air,
              before - t.getHealth(),
              before,
              t.getHealth());
      t.discard();
    }
    negatives(server, p);
    isolation(server, p);
    interaction(server, p);
    summonedSwordInteraction(server, p);
    lateAndInvalidated(server, p);
    highBudgets(server, p);
    bosses(server, p);
    benchmark(server, p);
    traceScopes(l);
    check(
        !DamageObservationService.active()
            && !NativeAttackOrigin.active()
            && SoulStrikeContext.current() == null,
        "all permission scopes closed");
    p.getInventory().setItem(0, ItemStack.EMPTY);
    check(!AkatsukiDomainService.session(p).active(), "switch suspends");
  }

  public static boolean nativeHit(LivingEntity t, ServerPlayer p, float amount) {
    if (!Boolean.getBoolean("contractblade.soul.validation")
        && !Boolean.getBoolean("contractblade.soulVisual.validation"))
      throw new IllegalStateException("Controlled request outside fixture");
    var source = p.damageSources().playerAttack(p);
    try (var origin = NativeAttackOrigin.strike(p, "VALIDATION_CONTROLLED_REQUEST", source, t)) {
      return t.hurt(source, amount);
    }
  }

  private static void nativeSlashProof(ServerPlayer p) throws Exception {
    var l = p.serverLevel();
    advance(l, 240);
    var t = cap(l);
    double attack = p.getAttribute(Attributes.ATTACK_DAMAGE).getBaseValue();
    try {
      p.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(100);
      p.moveTo(1, 150, .5, 0, 0);
      Class.forName("mods.flammpfeil.slashblade.util.AttackManager")
          .getMethod(
              "doMeleeAttack",
              LivingEntity.class,
              Entity.class,
              boolean.class,
              boolean.class,
              float.class)
          .invoke(null, p, t, true, false, 1f);
      var observation = DamageObservationService.latest(p.getServer());
      check(
          observation != null
              && observation.target().equals(t.getUUID())
              && observation.raw() > 5
              && observation.recoverable(),
          "native SlashBlade actual hurt provenance observation="+observation);
      // Actual SlashBlade AttackManager provenance (unlike synthetic direct
      // damage validation) must earn a native-hit resonance based on HP dealt,
      // without feeding the blocked theoretical deficit into that resonance.
      double actualEcho = observation.after() - t.getHealth();
      double allowedEcho = SoulResonancePolicy.requested(
          observation.actual(), AkatsukiDomainGrowth.parameter(p, "resonance_hit_ratio"));
      check(actualEcho > .01 && actualEcho <= allowedEcho + .025,
          "real SlashBlade native-hit resonance = " + actualEcho + " max=" + allowedEcho);
      LogUtils.getLogger()
          .info(
              "SOUL_NATIVE_SLASHBLADE_INVOCATION_PASS raw={} E={} A={} origin={}",
              observation.raw(),
              observation.effective(),
              observation.actual(),
              observation.proof().origin());
    } finally {
      p.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(attack);
      t.discard();
    }
  }

  public static void nativeInvocationProof(MinecraftServer server) {
    var l = server.overworld();
    var p =
        new net.minecraftforge.common.util.FakePlayer(
            l,
            new GameProfile(
                UUID.nameUUIDFromBytes(
                    "PlainNativeProof".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                "PlainNativeProof"));
    p.getInventory().selected = 0;
    p.getInventory().setItem(0, new ItemStack(Items.IRON_SWORD));
    p.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(20);
    p.getAttribute(Attributes.ATTACK_SPEED).setBaseValue(1024);
    p.moveTo(1, 150, .5, 0, 0);
    var target = new NativeProbe(l);
    prepare(target);
    l.addFreshEntity(target);
    float before = target.getHealth();
    p.attack(target);
    check(
        target.proved && target.getHealth() < before && !NativeAttackOrigin.active(),
        "actual Player.attack invocation capability");
    target.discard();
    LogUtils.getLogger().info("SOUL_NATIVE_PLAYER_INVOCATION_PASS");
  }

  private static final class NativeProbe extends Zombie {
    boolean proved;

    NativeProbe(ServerLevel l) {
      super(EntityType.ZOMBIE, l);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
      if (source.getEntity() instanceof ServerPlayer p)
        proved |= NativeAttackOrigin.claim(p, p.getMainHandItem(), source, this);
      return super.hurt(source, amount);
    }
  }

  private static ServerPlayer player(ServerLevel l, String name) throws Exception {
    var p =
        new net.minecraftforge.common.util.FakePlayer(
            l,
            new GameProfile(
                UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                name));
    p.moveTo(.5, 150, .5, 0, 0);
    p.getInventory().selected = 0;
    AkatsukiDomainValidation.prepareBlade(p);
    p.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(8);
    p.getAttribute(Attributes.ATTACK_SPEED).setBaseValue(1024);
    setLevel(p, 2);
    SoulAnchorService.fixtureOwner(p);
    return p;
  }

  private static void setLevel(ServerPlayer p, int n) {
    ContractMasteryService.setLevel(p.getServer().createCommandSourceStack(), p, n);
  }

  private static void negatives(MinecraftServer server, ServerPlayer p) {
    setLevel(p, 2);
    var l = p.serverLevel();
    for (String mode :
        List.of(
            "plain",
            "armor",
            "resistance",
            "iframes",
            "event_reduced",
            "event_canceled",
            "custom_early_rejection",
            "nonstandard_health")) {
      advance(l, 240);
      Zombie t =
          mode.equals("custom_early_rejection")
              ? cap(l)
              : mode.equals("nonstandard_health") ? new CustomHealth(l) : zombie(l);
      if (t instanceof CustomHealth) {
        prepare(t);
        l.addFreshEntity(t);
      }
      if (t instanceof Capped c) c.blocked = true;
      if (mode.equals("armor")) t.getAttribute(Attributes.ARMOR).setBaseValue(30);
      if (mode.equals("resistance"))
        t.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 3));
      if (mode.equals("iframes")) {
        t.invulnerableTime = 20;
        t.hurtTime = 10;
      }
      if (mode.equals("event_reduced")) reduced = t;
      if (mode.equals("event_canceled")) canceled = t;
      float hp = t.getHealth();
      nativeHit(t, p, 100);
      check(
          !SoulDamageDebtService.hasDebt(p, t) && SoulAnchorService.create(p, t) == null,
          "no invented debt " + mode);
      LogUtils.getLogger()
          .info(
              "SOUL_PROVENANCE_NEGATIVE case={} before={} after={} observation={}",
              mode,
              hp,
              t.getHealth(),
              DamageObservationService.latest(server));
      reduced = canceled = null;
      t.discard();
    }
    var unscoped = cap(l);
    unscoped.hurt(p.damageSources().playerAttack(p), 100);
    check(
        !SoulDamageDebtService.hasDebt(p, unscoped),
        "unscoped player source cannot borrow current blade");
    unscoped.discard();
    var t = cap(l);
    t.getAttribute(Attributes.ARMOR).setBaseValue(20);
    nativeHit(t, p, 120);
    var o = DamageObservationService.latest(server);
    check(
        o.recoverable()
            && o.effective() < 120
            && o.effective() > 80
            && near(o.loss(), o.effective() - o.actual()),
        "armor before cap uses E not raw");
    t.discard();
    var unknown = new UnknownPreCap(l);
    prepare(unknown);
    l.addFreshEntity(unknown);
    nativeHit(unknown, p, 100);
    check(!SoulDamageDebtService.hasDebt(p, unknown), "unwrapped outer entry fail closed");
    unknown.discard();
    var nonlinear = new Nonlinear(l);
    prepare(nonlinear);
    l.addFreshEntity(nonlinear);
    nativeHit(nonlinear, p, 100);
    check(!SoulDamageDebtService.hasDebt(p, nonlinear), "unknown normal defense fail closed");
    nonlinear.discard();
    t = cap(l);
    t.setAbsorptionAmount(8);
    nativeHit(t, p, 100);
    o = DamageObservationService.latest(server);
    check(
        o.recoverable() && near(o.effective(), 92) && near(o.actual(), 0),
        "original absorption excluded");
    t.setAbsorptionAmount(20);
    check(!SoulDamageDebtService.hasDebt(p, t), "new shield invalidates receipt");
    t.discard();
    t = cap(l);
    t.protectedPhase = true;
    nativeHit(t, p, 100);
    check(!SoulDamageDebtService.hasDebt(p, t), "protected phase not debt");
    t.discard();
  }

  private static void isolation(MinecraftServer server, ServerPlayer p) {
    var l = p.serverLevel();
    var t = cap(l);
    canceled = t;
    nativeHit(t, p, 100);
    canceled = null;
    nativeHit(t, foreign, 100);
    check(
        !SoulDamageDebtService.hasDebt(p, t) && SoulDamageDebtService.hasDebt(foreign, t),
        "same tick failed A cannot steal B");
    t.discard();
    t = cap(l);
    canceled = t;
    nativeHit(t, p, 100);
    canceled = null;
    t.hurt(p.damageSources().generic(), 100);
    check(!SoulDamageDebtService.hasDebt(p, t), "environment cannot confirm failed hit");
    t.discard();
    t = cap(l);
    nested = t;
    nativeHit(t, p, 100);
    check(!SoulDamageDebtService.hasDebt(p, t), "nested foreign HP subtracted");
    t.discard();
    t = cap(l);
    throwing = t;
    try {
      nativeHit(t, p, 100);
    } catch (IllegalStateException expected) {
    }
    throwing = null;
    check(
        !DamageObservationService.active() && !SoulDamageDebtService.hasDebt(p, t),
        "exception clears observation");
    t.discard();
    var a = cap(l);
    var b = cap(l);
    nativeHit(a, p, 100);
    long first = DamageObservationService.latest(server).sequence();
    nativeHit(b, p, 100);
    check(
        first != DamageObservationService.latest(server).sequence()
            && SoulDamageDebtService.hasDebt(p, a)
            && SoulDamageDebtService.hasDebt(p, b),
        "area hits independent sequences");
    a.discard();
    b.discard();
    t = cap(l);
    var arrow = new net.minecraft.world.entity.projectile.Arrow(l, p);
    t.hurt(p.damageSources().arrow(arrow, p), 100);
    check(!SoulDamageDebtService.hasDebt(p, t), "unstamped arrow no guessed ownership");
    t.invulnerableTime = 0;
    com.maidweapon.forge.system.mastery.ContractCombatCredits.stampLaunch(
        arrow,
        new com.maidweapon.forge.api.ContractCombatCreditResolver.Credit(p, p.getMainHandItem()));
    t.hurt(p.damageSources().arrow(arrow, p), 100);
    check(SoulDamageDebtService.hasDebt(p, t), "registered launch proof");
    var original = p.getMainHandItem();
    p.getInventory().setItem(0, original.copy());
    check(!SoulDamageDebtService.hasDebt(p, t), "copy cannot inherit carrier identity");
    p.getInventory().setItem(0, original);
    t.discard();
  }

  private static void interaction(MinecraftServer server, ServerPlayer p) {
    var l = p.serverLevel();
    advance(l, 240);
    var t = cap(l);
    nativeHit(t, p, 100);
    var c = SoulAnchorService.create(p, t);
    check(c != null, "receipt core");
    check(!c.hurt(p.damageSources().playerAttack(p), 100),
        "raw damage source cannot activate core");
    float hp = t.getHealth();
    p.moveTo(c.getX(), t.getY(), c.getZ() - 1, 0, -90);
    p.setOnGround(true);
    p.attack(c);
    check(near(hp, t.getHealth()) && c.burstUntil == 0, "ground sweep cannot trigger");
    aim(foreign, c);
    foreign.attack(c);
    check(c.burstUntil == 0, "foreign cannot hijack");
    aim(p, c);
    p.setYRot(180);
    p.setXRot(70);
    p.attack(c);
    check(c.burstUntil == 0, "wrong ray rejected");
    t.setPos(t.position().add(2, 0, 0));
    SoulAnchorService.follow(c);
    check(Math.abs(c.getX() - t.getX()) < .1, "follows target");
    aim(p, c);
    p.attack(c);
    check(c.burstUntil > 0, "native direct click starts");
    p.attack(c);
    for (int i = 0; i < 14; i++) {
      advance(l, 1);
      SoulBurstService.tick(new TickEvent.ServerTickEvent(TickEvent.Phase.END, () -> true, server));
    }
    check(near(hp - t.getHealth(), 28.5), "single click complete cumulative no repeat");
    t.discard();
    advance(l, 240);
    t = cap(l);
    nativeHit(t, p, 100);
    c = SoulAnchorService.create(p, t);
    t.phase++;
    check(
        !SoulDamageDebtService.hasDebt(p, t) && !SoulAnchorService.valid(c),
        "phase invalidates old debt");
    t.discard();
    advance(l, 240);
    t = cap(l);
    nativeHit(t, p, 100);
    var receipt = DamageObservationService.latest(server);
    SoulDamageDebtService.invalidate(p);
    net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new DamageObservedEvent(receipt, t));
    check(!SoulDamageDebtService.hasDebt(p, t), "receipt replay cannot reset invalidated debt");
    t.discard();
  }

  /**
   * Actual SlashBlade registry projectile + native ContractCombatCredits launch
   * stamping. Simulates its next swept movement without relying on renderer frames.
   */
  private static void summonedSwordInteraction(MinecraftServer server, ServerPlayer p) {
    var l = p.serverLevel();
    setLevel(p, 2);
    advance(l, 240);
    var t = cap(l);
    p.moveTo(t.getX() - 3, t.getY(), t.getZ(), 0, 0);
    p.setOnGround(true); // Projectiles intentionally do NOT require the owner to jump.
    nativeHit(t, p, 100);
    var core = SoulAnchorService.create(p, t);
    check(core != null && core.getY() >= t.getBoundingBox().maxY + 4,
        "summoned sword high core");
    var summonType = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(
        net.minecraft.resources.ResourceLocation.parse("slashblade:abstract_summoned_sword"));
    check(summonType != null && summonType != EntityType.PIG,
        "SlashBlade native summoned sword entity ID available");
    var foreignShot = summonType.create(l);
    check(foreignShot instanceof net.minecraft.world.entity.projectile.Projectile,
        "native summoned sword must be projectile");
    var foreignProjectile = (net.minecraft.world.entity.projectile.Projectile) foreignShot;
    foreignProjectile.setOwner(foreign);
    foreignProjectile.moveTo(core.getX() - 4, core.getY() + .9, core.getZ(), 0, 0);
    foreignProjectile.setDeltaMovement(6, 0, 0);
    l.addFreshEntity(foreignProjectile);
    check(!SoulAnchorProjectileService.sweep(l, core) && core.burstUntil == 0,
        "foreign native sword cannot trigger owner's core");
    foreignProjectile.discard();

    // Non-SlashBlade projectiles must not become an implicit authorization token.
    var arrow = new net.minecraft.world.entity.projectile.Arrow(l, p);
    arrow.setPos(core.getX() - 4, core.getY() + .9, core.getZ());
    arrow.setDeltaMovement(6, 0, 0);
    l.addFreshEntity(arrow);
    check(!SoulAnchorProjectileService.sweep(l, core) && core.burstUntil == 0,
        "ordinary projectile cannot trigger core");
    arrow.discard();

    // A locked-on boss would otherwise redirect the native sword away from a
    // high core. Explicit player aim must retarget a fresh sword at launch.
    var targetDirection = core.getBoundingBox().getCenter().subtract(p.getEyePosition());
    p.setYRot((float) Math.toDegrees(Math.atan2(-targetDirection.x, targetDirection.z)));
    p.setXRot((float) -Math.toDegrees(Math.atan2(targetDirection.y,
        Math.sqrt(targetDirection.x * targetDirection.x
            + targetDirection.z * targetDirection.z))));
    var lockedShot = summonType.create(l);
    check(lockedShot instanceof net.minecraft.world.entity.projectile.Projectile,
        "locked native summoned sword must be projectile");
    var lockedSword = (net.minecraft.world.entity.projectile.Projectile) lockedShot;
    lockedSword.setOwner(p);
    lockedSword.setPos(p.getX(), p.getEyeY(), p.getZ());
    lockedSword.setDeltaMovement(3, 0, 0); // boss-lock direction, NOT the high core
    l.addFreshEntity(lockedSword);
    check(!SoulAnchorProjectileService.sweep(l, core) && core.burstUntil == 0,
        "distant native shot cannot trigger without physically reaching the core");
    var expectedFlight = core.getBoundingBox().getCenter().subtract(lockedSword.position()).normalize();
    check(lockedSword.getDeltaMovement().normalize().dot(expectedFlight) > .99,
        "player crosshair overrides native locked-boss sword trajectory");
    lockedSword.discard();

    var ownedShot = summonType.create(l);
    check(ownedShot instanceof net.minecraft.world.entity.projectile.Projectile,
        "owned native summoned sword must be projectile");
    var sword = (net.minecraft.world.entity.projectile.Projectile) ownedShot;
    sword.setOwner(p);
    sword.moveTo(core.getX() - 4, core.getY() + .9, core.getZ(), 0, 0);
    sword.setDeltaMovement(6, 0, 0);
    l.addFreshEntity(sword);
    var evidence = com.maidweapon.forge.system.mastery.ContractCombatCredits.resolveHit(
        p.damageSources().indirectMagic(sword, p));
    check(evidence != null && evidence.player() == p,
        "native projectile launch must retain exact carrier proof");
    float hp = t.getHealth();
    check(SoulAnchorProjectileService.sweep(l, core) && core.burstUntil > 0,
        "owner's launched summoned sword triggers soul core from the ground");
    check(sword.isRemoved() && !SoulAnchorProjectileService.sweep(l, core),
        "one sword cannot trigger a second burst");
    for (int i = 0; i < 14; i++) {
      advance(l, 1);
      SoulBurstService.tick(new TickEvent.ServerTickEvent(TickEvent.Phase.END, () -> true, server));
    }
    check(near(hp - t.getHealth(), 28.5),
        "summoned sword uses identical bounded verified debt as direct melee");
    t.discard();
    LogUtils.getLogger().info("SOUL_NATIVE_SUMMONED_SWORD_PASS");
  }

  private static void lateAndInvalidated(MinecraftServer server, ServerPlayer p) {
    var l = p.serverLevel();
    setLevel(p, 2);
    advance(l, 240);
    var t = cap(l);
    nativeHit(t, p, 100);
    var o = DamageObservationService.latest(server);
    new net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket(1).handle(p.connection);
    new net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket(0).handle(p.connection);
    check(
        !SoulDamageDebtService.hasDebt(p, t),
        "native same-tick hotbar switch invalidates old weapon permission");
    net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new DamageObservedEvent(o, t));
    check(!SoulDamageDebtService.hasDebt(p, t), "native switch receipt cannot replay");
    t.discard();
    advance(l, 240);
    t = cap(l);
    nativeHit(t, p, 100);
    t.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 0));
    check(!SoulDamageDebtService.hasDebt(p, t), "new normal resistance invalidates old receipt");
    t.discard();
    int lifetime = SoulDamageConfig.ANCHOR_LIFETIME.get();
    try {
      SoulDamageConfig.ANCHOR_LIFETIME.set(200);
      advance(l, 240);
      t = cap(l);
      nativeHit(t, p, 100);
      var core = SoulAnchorService.create(p, t);
      float hp = t.getHealth();
      advance(l, 119);
      aim(p, core);
      p.attack(core);
      for (int i = 0; i < 14; i++) {
        advance(l, 1);
        SoulDamageDebtService.tick(
            new TickEvent.ServerTickEvent(TickEvent.Phase.END, () -> true, server));
        SoulBurstService.tick(
            new TickEvent.ServerTickEvent(TickEvent.Phase.END, () -> true, server));
      }
      check(near(hp - t.getHealth(), 28.5), "reserved burst survives ordinary receipt expiry");
      t.discard();
    } finally {
      SoulDamageConfig.ANCHOR_LIFETIME.set(lifetime);
    }
    advance(l, 240);
    t = cap(l);
    nativeHit(t, p, 100);
    var core = SoulAnchorService.create(p, t);
    aim(p, core);
    p.attack(core);
    SoulBurstService.tick(new TickEvent.ServerTickEvent(TickEvent.Phase.END, () -> true, server));
    float hp = t.getHealth();
    t.phase++;
    for (int i = 0; i < 14; i++) {
      advance(l, 1);
      SoulBurstService.tick(new TickEvent.ServerTickEvent(TickEvent.Phase.END, () -> true, server));
    }
    check(
        near(t.getHealth(), hp) && core.isRemoved(),
        "phase change cancels remaining reserved stages");
    t.discard();
    LogUtils.getLogger().info("SOUL_NATIVE_PACKET_AND_LATE_BURST_PASS");
  }

  /** Native Forge fixture: proved soul debt is NEVER clipped at legacy HP limits. */
  private static void highBudgets(MinecraftServer server, ServerPlayer p) {
    var l = p.serverLevel();
    setLevel(p, 10);
    for (double total : new double[] {128, 384, 512, 1024, 10000}) {
      advance(l, 240);
      var t = cap(l);
      // Vanilla MAX_HEALTH is final/bounded by its own 1024 HP attribute.
      // Verify ALL recoverable health, including >512 HP, without rewriting
      // production entity health internals just to exceed this fixture cap.
      float startingHealth = (float) Math.min(1024, Math.max(1000, total + 10));
      t.getAttribute(Attributes.MAX_HEALTH).setBaseValue(startingHealth);
      t.setHealth(startingHealth);
      double verifiedDebt = Math.min(total, startingHealth - 5);
      nativeHit(t, p, (float) (total + 5));
      var o = DamageObservationService.latest(server);
      check(o != null && o.recoverable() && near(o.loss(), verifiedDebt),
          "uncapped verified debt raw=" + total + " observed=" + o);
      var c = SoulAnchorService.create(p, t);
      check(c != null, "uncapped core " + total);
      aim(p, c);
      float hp = t.getHealth();
      p.attack(c);
      for (int i = 0; i < 14; i++) {
        advance(l, 1);
        SoulBurstService.tick(new TickEvent.ServerTickEvent(TickEvent.Phase.END, () -> true, server));
      }
      check(near(hp - t.getHealth(), verifiedDebt),
          "uncapped burst debt=" + verifiedDebt + " actual=" + (hp - t.getHealth()));
      LogUtils.getLogger().info("SOUL_UNCAPPED_RECOVERY raw={} debt={} actualHP={}",
          total, verifiedDebt, hp - t.getHealth());
      t.discard();
    }
  }

  private static void bosses(MinecraftServer server, ServerPlayer p) throws Exception {
    var l = p.serverLevel();
    for (String id :
        List.of(
            "cataclysm:ignis",
            "legendary_monsters:cloud_golem",
            "legendary_monsters:the_obliterator",
            "goety:apostle")) {
      var type =
          net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(
              net.minecraft.resources.ResourceLocation.parse(id));
      if (type == null || type == EntityType.PIG) {
        LogUtils.getLogger().info("SOUL_BOSS_NOT_INSTALLED {}", id);
        continue;
      }
      for (int level : new int[] {1, 2, 10}) {
        setLevel(p, level);
        advance(l, 240);
        var entity = type.create(l);
        if (!(entity instanceof LivingEntity t)) continue;
        if (t instanceof Mob mob) mob.setNoAi(true);
        t.moveTo(3, 150, .5, 0, 0);
        l.addFreshEntity(t);
        if (id.endsWith("cloud_golem")) {
          try {
            t.getClass().getMethod("setIsSleep", boolean.class).invoke(t, false);
          } catch (NoSuchMethodException ignored) {
          }
        }
        p.moveTo(1, 150, .5, 0, 0);
        float before = t.getHealth();
        nativeHit(t, p, 100);
        var o = DamageObservationService.latest(server);
        double nativeLoss = before - t.getHealth();
        if (!o.recoverable()) {
          LogUtils.getLogger()
              .info(
                  "SOUL_BOSS_UNSUPPORTED id={} classification={} observation={}",
                  id,
                  o.classification(),
                  o);
          t.discard();
          continue;
        }
        var s = AkatsukiDomainService.session(p);
        s.contact(l.getGameTime(), t.getUUID());
        int originalInvulnerable = t.invulnerableTime, originalHurt = t.hurtTime;
        if (id.endsWith("cloud_golem"))
          t.getClass().getField("GolemInvulnerabilityTime").setInt(t, 60);
        var cut = SoulDamageEngine.cut(p, t, s);
        check(
            t.invulnerableTime == originalInvulnerable && t.hurtTime == originalHurt,
            "vanilla hit timers restored");
        if (id.endsWith("cloud_golem"))
          check(
              t.getClass().getField("GolemInvulnerabilityTime").getInt(t) == 60,
              "native golem timer restored");
        check(
            cut != null && near(cut.loss(), o.loss() * SoulRecoveryPolicy.cut(level)),
            "real cut " + id);
        double air = 0;
        if (level >= 2) {
          var core = SoulAnchorService.create(p, t);
          check(core != null, "real boss core " + id);
          aim(p, core);
          float hp = t.getHealth();
          p.attack(core);
          for (int n = 0; n < 14; n++) {
            advance(l, 1);
            SoulBurstService.tick(
                new TickEvent.ServerTickEvent(TickEvent.Phase.END, () -> true, server));
          }
          air = hp - t.getHealth();
          check(
              near(cut.loss() + air, o.loss() * SoulRecoveryPolicy.aerial(level)),
              "real aerial " + id);
        }
        LogUtils.getLogger()
            .info(
                "SOUL_REAL_BOSS_PROVENANCE id={} level={} E={} A={} L={} cut={} core={} HP={}→{}",
                id,
                level,
                o.effective(),
                nativeLoss,
                o.loss(),
                cut.loss(),
                air,
                before,
                t.getHealth());
        if (level == 10 && id.endsWith("ignis")) {
          var animation = t.getClass().getField("PHASE_2").get(null);
          t.getClass().getMethod("setAnimation", animation.getClass()).invoke(t, animation);
          t.invulnerableTime = 0;
          nativeHit(t, p, 100);
          check(
              !SoulDamageDebtService.hasDebt(p, t)
                  && !DamageObservationService.latest(server).recoverable(),
              "real Ignis initialization is not debt");
          LogUtils.getLogger().info("SOUL_REAL_PROTECTED_PHASE_PASS {}", id);
        }
        t.discard();
      }
    }
  }

  private static void benchmark(MinecraftServer server, ServerPlayer p) {
    setLevel(p, 1);
    var l = p.serverLevel();
    var t = cap(l);
    long[] times = new long[200];
    int delivered = 0;
    for (int i = 0; i < 220; i++) {
      advance(l, 20);
      t.setHealth(1000);
      t.invulnerableTime = 0;
      long start = System.nanoTime();
      nativeHit(t, p, 100);
      var s = AkatsukiDomainService.session(p);
      s.contact(l.getGameTime(), t.getUUID());
      var f = SoulDamageEngine.cut(p, t, s);
      if (i >= 20) {
        times[i - 20] = System.nanoTime() - start;
        if (f != null && f.success()) delivered++;
      }
    }
    Arrays.sort(times);
    check(delivered == 200, "persistent budget replenishes");
    LogUtils.getLogger()
        .info(
            "SOUL_PROVENANCE_BENCHMARK samples=200 delivered={} meanMicros={} p95Micros={}"
                + " maxMicros={}",
            delivered,
            Arrays.stream(times).average().orElse(0) / 1000,
            times[189] / 1000d,
            times[199] / 1000d);
    t.discard();
  }

  private static void traceScopes(ServerLevel l) {
    var a = new net.minecraft.world.entity.projectile.Arrow(l, 0, 150, 0);
    var b = new net.minecraft.world.entity.projectile.Arrow(l, 0, 150, 0);
    try (var outer = com.maidweapon.forge.system.fox.challenge.BlackFoxAttackTrace.open(a)) {
      try (var inner = com.maidweapon.forge.system.fox.challenge.BlackFoxAttackTrace.open(b)) {
        check(
            com.maidweapon.forge.system.fox.challenge.BlackFoxAttackTrace.projectile() == b,
            "nested projectile");
      }
      check(
          com.maidweapon.forge.system.fox.challenge.BlackFoxAttackTrace.projectile() == a,
          "restore parent projectile");
    }
    try (var ignored = com.maidweapon.forge.system.fox.challenge.BlackFoxAttackTrace.open(a)) {
      throw new IllegalStateException("EXPECTED_PROJECTILE_EXCEPTION");
    } catch (IllegalStateException expected) {
    }
    check(
        com.maidweapon.forge.system.fox.challenge.BlackFoxAttackTrace.projectile() == null,
        "no projectile leak");
    LogUtils.getLogger().info("SOUL_TRACE_EXCEPTION_PASS");
  }

  private static void aim(ServerPlayer p, SoulAnchorEntity c) {
    p.moveTo(c.getX(), c.getY() - .7, c.getZ() - 2.2, 0, 0);
    p.setOnGround(false);
    var d = c.getBoundingBox().getCenter().subtract(p.getEyePosition());
    p.setYRot((float) Math.toDegrees(Math.atan2(-d.x, d.z)));
    p.setXRot((float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z))));
  }

  private static Capped cap(ServerLevel l) {
    var t = new Capped(l);
    prepare(t);
    t.getAttribute(Attributes.ARMOR).setBaseValue(0);
    l.addFreshEntity(t);
    return t;
  }

  private static Zombie zombie(ServerLevel l) {
    var t = EntityType.ZOMBIE.create(l);
    prepare(t);
    l.addFreshEntity(t);
    return t;
  }

  private static void prepare(Zombie t) {
    t.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
    t.setHealth(1000);
    t.setNoAi(true);
    t.moveTo(3, 150, .5, 0, 0);
  }

  private static void advance(ServerLevel l, int ticks) {
    ((net.minecraft.world.level.storage.ServerLevelData) l.getLevelData())
        .setGameTime(l.getGameTime() + ticks);
  }

  private static boolean near(double a, double b) {
    return Math.abs(a - b) < .025;
  }

  private static void check(boolean ok, String detail) {
    if (!ok) throw new IllegalStateException(detail);
  }

  private static class Capped extends Zombie {
    float bucket;
    long bucketTick;
    boolean blocked, protectedPhase;
    int phase;

    Capped(ServerLevel l) {
      super(EntityType.ZOMBIE, l);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
      if (level().getGameTime() - bucketTick >= 20) {
        bucket = 0;
        bucketTick = level().getGameTime();
      }
      try (var scope =
          DamageObservationService.open(this, source, amount, Capped.class.getName())) {
        try {
          if (blocked) return false;
          if (!source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            float limited = Math.min(amount, Math.min(5, Math.max(0, 10 - bucket)));
            DamageObservationService.capped(amount, 5, limited);
            amount = limited;
            bucket += amount;
          }
          boolean result = amount > 0 && super.hurt(source, amount);
          scope.returned(result);
          return result;
        } catch (RuntimeException | Error e) {
          scope.failed();
          throw e;
        }
      }
    }
  }

  private static final class UnknownPreCap extends Capped {
    UnknownPreCap(ServerLevel l) {
      super(l);
    }

    @Override
    public boolean hurt(DamageSource s, float n) {
      return super.hurt(s, n * .5f);
    }
  }

  private static final class Nonlinear extends Capped {
    Nonlinear(ServerLevel l) {
      super(l);
    }

    @Override
    protected float getDamageAfterArmorAbsorb(DamageSource s, float n) {
      return super.getDamageAfterArmorAbsorb(s, n) * (n > 10 ? .5f : 1);
    }
  }

  private static final class CustomHealth extends Zombie {
    float hp = 1000;

    CustomHealth(ServerLevel l) {
      super(EntityType.ZOMBIE, l);
    }

    @Override
    public float getHealth() {
      return hp;
    }

    @Override
    public void setHealth(float n) {
      hp = Math.max(0, n);
    }
  }

  private SoulProvenanceValidation() {}
}
