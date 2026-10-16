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
    var l = server.overworld();
    var p = player(l, "ProvenanceOwner");
    foreign = player(l, "ProvenanceForeign");
    for (int level = 1; level <= 10; level++) {
      setLevel(p, level);
      advance(l, 240);
      var t = cap(l);
      float before = t.getHealth();
      t.hurt(p.damageSources().playerAttack(p), 100);
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
        check(core != null && core.getY() >= t.getBoundingBox().maxY + 1.5, "aerial top placement");
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
    highBudgets(server, p);
    bosses(server, p);
    benchmark(server, p);
    traceScopes(l);
    check(
        !DamageObservationService.active() && SoulStrikeContext.current() == null,
        "all permission scopes closed");
    p.getInventory().setItem(0, ItemStack.EMPTY);
    check(!AkatsukiDomainService.session(p).active(), "switch suspends");
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
      t.hurt(p.damageSources().playerAttack(p), 100);
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
    var t = cap(l);
    t.getAttribute(Attributes.ARMOR).setBaseValue(20);
    t.hurt(p.damageSources().playerAttack(p), 120);
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
    unknown.hurt(p.damageSources().playerAttack(p), 100);
    check(!SoulDamageDebtService.hasDebt(p, unknown), "unwrapped outer entry fail closed");
    unknown.discard();
    var nonlinear = new Nonlinear(l);
    prepare(nonlinear);
    l.addFreshEntity(nonlinear);
    nonlinear.hurt(p.damageSources().playerAttack(p), 100);
    check(!SoulDamageDebtService.hasDebt(p, nonlinear), "unknown normal defense fail closed");
    nonlinear.discard();
    t = cap(l);
    t.setAbsorptionAmount(8);
    t.hurt(p.damageSources().playerAttack(p), 100);
    o = DamageObservationService.latest(server);
    check(
        o.recoverable() && near(o.effective(), 92) && near(o.actual(), 0),
        "original absorption excluded");
    t.setAbsorptionAmount(20);
    check(!SoulDamageDebtService.hasDebt(p, t), "new shield invalidates receipt");
    t.discard();
    t = cap(l);
    t.protectedPhase = true;
    t.hurt(p.damageSources().playerAttack(p), 100);
    check(!SoulDamageDebtService.hasDebt(p, t), "protected phase not debt");
    t.discard();
  }

  private static void isolation(MinecraftServer server, ServerPlayer p) {
    var l = p.serverLevel();
    var t = cap(l);
    canceled = t;
    t.hurt(p.damageSources().playerAttack(p), 100);
    canceled = null;
    t.hurt(foreign.damageSources().playerAttack(foreign), 100);
    check(
        !SoulDamageDebtService.hasDebt(p, t) && SoulDamageDebtService.hasDebt(foreign, t),
        "same tick failed A cannot steal B");
    t.discard();
    t = cap(l);
    canceled = t;
    t.hurt(p.damageSources().playerAttack(p), 100);
    canceled = null;
    t.hurt(p.damageSources().generic(), 100);
    check(!SoulDamageDebtService.hasDebt(p, t), "environment cannot confirm failed hit");
    t.discard();
    t = cap(l);
    nested = t;
    t.hurt(p.damageSources().playerAttack(p), 100);
    check(!SoulDamageDebtService.hasDebt(p, t), "nested foreign HP subtracted");
    t.discard();
    t = cap(l);
    throwing = t;
    try {
      t.hurt(p.damageSources().playerAttack(p), 100);
    } catch (IllegalStateException expected) {
    }
    throwing = null;
    check(
        !DamageObservationService.active() && !SoulDamageDebtService.hasDebt(p, t),
        "exception clears observation");
    t.discard();
    var a = cap(l);
    var b = cap(l);
    a.hurt(p.damageSources().playerAttack(p), 100);
    long first = DamageObservationService.latest(server).sequence();
    b.hurt(p.damageSources().playerAttack(p), 100);
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
    t.hurt(p.damageSources().playerAttack(p), 100);
    var c = SoulAnchorService.create(p, t);
    check(c != null, "receipt core");
    check(
        !SoulAnchorService.strike(c, p.damageSources().playerAttack(p)),
        "source alone insufficient");
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
    t.hurt(p.damageSources().playerAttack(p), 100);
    c = SoulAnchorService.create(p, t);
    t.phase++;
    check(
        !SoulDamageDebtService.hasDebt(p, t) && !SoulAnchorService.valid(c),
        "phase invalidates old debt");
    t.discard();
    advance(l, 240);
    t = cap(l);
    t.hurt(p.damageSources().playerAttack(p), 100);
    var receipt = DamageObservationService.latest(server);
    SoulDamageDebtService.invalidate(p);
    net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new DamageObservedEvent(receipt, t));
    check(!SoulDamageDebtService.hasDebt(p, t), "receipt replay cannot reset invalidated debt");
    t.discard();
  }

  private static void highBudgets(MinecraftServer server, ServerPlayer p) {
    var l = p.serverLevel();
    setLevel(p, 10);
    double player = SoulDamageConfig.PLAYER_SECOND.get();
    try {
      SoulDamageConfig.PLAYER_SECOND.set(8192d);
      for (double total : new double[] {128, 384, 512, 1024}) {
        advance(l, 240);
        var t = cap(l);
        t.hurt(p.damageSources().playerAttack(p), (float) (total + 5));
        var c = SoulAnchorService.create(p, t);
        check(c != null, "high core");
        aim(p, c);
        float hp = t.getHealth();
        p.attack(c);
        for (int i = 0; i < 14; i++) {
          advance(l, 1);
          SoulBurstService.tick(
              new TickEvent.ServerTickEvent(TickEvent.Phase.END, () -> true, server));
        }
        double expected = Math.min(Math.min(total, 995), 512);
        check(
            near(hp - t.getHealth(), expected),
            "high stage total " + total + " actual=" + (hp - t.getHealth()));
        LogUtils.getLogger()
            .info("SOUL_HIGH_BUDGET requested={} actualHP={}", total, hp - t.getHealth());
        t.discard();
      }
    } finally {
      SoulDamageConfig.PLAYER_SECOND.set(player);
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
        t.hurt(p.damageSources().playerAttack(p), 100);
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
        var cut = SoulDamageEngine.cut(p, t, s);
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
      t.hurt(p.damageSources().playerAttack(p), 100);
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
