package com.maidweapon.forge.system.akatsuki;

import com.maidweapon.common.SoulDamageConfig;
import com.maidweapon.forge.compat.DomainTargetCompatibility;
import com.maidweapon.forge.entity.SoulAnchorEntity;
import com.maidweapon.forge.system.akatsuki.soul.*;
import com.maidweapon.forge.system.mastery.ContractMasteryService;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
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
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

/** Opt-in real Forge server, real HP and optional real boss JAR comparisons. */
@Mod.EventBusSubscriber(modid = "maid_weapon")
public final class SoulDamageValidation {
  private static LivingEntity reduce, cancel;
  private static boolean running;

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public static void reduction(LivingHurtEvent e) {
    if (running && e.getEntity() == reduce) e.setAmount(.25f);
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public static void cancel(LivingAttackEvent e) {
    if (running && e.getEntity() == cancel) e.setCanceled(true);
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public static void reductionFinal(LivingDamageEvent e) {
    if (running && e.getEntity() == reduce) e.setAmount(.1f);
  }

  @SubscribeEvent
  public static void started(ServerStartedEvent e) {
    if (!Boolean.getBoolean("contractblade.soul.validation")) return;
    running = true;
    try {
      run(e.getServer());
      LogUtils.getLogger().info("SOUL_SERVER_PASS");
    } catch (Throwable error) {
      LogUtils.getLogger().error("SOUL_SERVER_FAIL", error);
    } finally {
      running = false;
      reduce = null;
      cancel = null;
      e.getServer().halt(false);
    }
  }

  private static void run(MinecraftServer server) throws Exception {
    var level = server.overworld();
    var p =
        new net.minecraftforge.common.util.FakePlayer(
            level,
            new GameProfile(
                UUID.fromString("877e986c-c78b-42a1-8e4a-a6ac5d4308b1"), "SoulFixture"));
    p.moveTo(.5, 150, .5, 0, 0);
    p.getInventory().selected = 0;
    AkatsukiDomainValidation.prepareBlade(p);
    ContractMasteryService.setLevel(server.createCommandSourceStack(), p, 2);
    SoulAnchorService.fixtureOwner(p);
    p.getAttribute(Attributes.ATTACK_DAMAGE)
        .setBaseValue(8); // Controlled attack ability, not a damage request multiplier.
    for (String mode :
        List.of(
            "plain",
            "armor",
            "resistance",
            "iframes",
            "event_reduced",
            "event_canceled",
            "hit5_dps10",
            "custom_early_rejection",
            "nonstandard_health")) {
      advance(level, 220);
      Zombie t =
          mode.equals("hit5_dps10") || mode.equals("custom_early_rejection")
              ? new Capped(level, mode.equals("custom_early_rejection"))
              : mode.equals("nonstandard_health")
                  ? new CustomHealth(level)
                  : EntityType.ZOMBIE.create(level);
      t.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
      t.setHealth(200);
      t.setNoAi(true);
      t.moveTo(3, 150, .5, 0, 0);
      level.addFreshEntity(t);
      if (mode.equals("armor")) {
        t.getAttribute(Attributes.ARMOR).setBaseValue(30);
        t.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(20);
      }
      if (mode.equals("resistance"))
        t.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 3));
      if (mode.equals("event_reduced")) reduce = t;
      if (mode.equals("event_canceled")) cancel = t;
      if (mode.equals("iframes")) {
        t.hurt(p.damageSources().playerAttack(p), 10);
        t.setHealth(200);
        t.invulnerableTime = 20;
      }
      float before = t.getHealth();
      boolean ordinary = t.hurt(p.damageSources().playerAttack(p), 10);
      double normal = before - t.getHealth();
      t.invulnerableTime = 20;
      var s = AkatsukiDomainService.session(p);
      s.contact(level.getGameTime(), t.getUUID());
      var result = SoulDamageEngine.cut(p, t, s);
      check(result != null, "cut request " + mode);
      double cut = result.loss();
      check(!SoulDamageEngine.applying() && SoulStrikeContext.current() == null, "scope cleaned");
      float probe = t.getHealth();
      t.invulnerableTime = 0;
      t.hurt(p.damageSources().playerAttack(p), 10);
      if (mode.equals("event_canceled"))
        check(t.getHealth() == probe, "unrelated attacks still cancellable");
      reduce = null;
      cancel = null;
      t.invulnerableTime = 0;
      // A genuine native hit confirms the core, then the owner attacks its actual world hitbox.
      if (t instanceof Capped c) c.blocked = false;
      t.hurt(p.damageSources().playerAttack(p), 1);
      SoulAnchorService.confirm(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, p));
      var cores = level.getEntitiesOfClass(SoulAnchorEntity.class, t.getBoundingBox().inflate(6));
      check(!cores.isEmpty(), "native hit creates attackable core " + mode);
      var core = cores.get(0);
      float burstBefore = t.getHealth();
      if (mode.equals("event_reduced")) reduce = t;
      if (mode.equals("event_canceled")) cancel = t;
      if (mode.equals("custom_early_rejection")) ((Capped) t).blocked = true;
      check(
          SoulAnchorService.strike(core, p.damageSources().playerAttack(p)),
          "world melee core hit");
      check(
          !SoulAnchorService.strike(core, p.damageSources().playerAttack(p)),
          "same-frame duplicate blocked");
      // The soul stays where it was peeled, while the target moves out of melee range.
      var fixed = core.position();
      t.setPos(24, 150, .5);
      for (int tick = 0; tick < 14; tick++) {
        advance(level, 1);
        SoulBurstService.tick(
            new TickEvent.ServerTickEvent(TickEvent.Phase.END, () -> true, server));
        // Manual Lv2 stages do not call the old automatic selector. Their fresh feedback must
        // survive a pruning boundary just as Lv5 automatic feedback does.
        if (tick == 0 && mode.equals("plain")) {
          SoulDamageEngine.prune(level.getGameTime());
          check(
              SoulDamageEngine.best(t, DomainDamageAdaptation.Channel.ABSOLUTE)
                  == DomainDamageAdaptation.Channel.SLASH,
              "fresh manual-burst feedback survives pruning");
        }
      }
      double burst = burstBefore - t.getHealth();
      reduce = null;
      cancel = null;
      check(
          core.position().equals(fixed) && core.isRemoved(),
          "stationary core shatters after finite burst");
      check(
          !SoulDamageEngine.burstReady(t.getUUID(), level.getGameTime()),
          "target cooldown shared after burst");
      if (!mode.equals("custom_early_rejection") && !mode.equals("nonstandard_health"))
        check(cut > 0, "actual soul HP loss " + mode);
      if (mode.equals("custom_early_rejection"))
        check(cut == 0 && burst == 0, "unadapted early rejection honestly remains unsupported");
      LogUtils.getLogger()
          .info(
              "SOUL_HP_COMPARISON fixture={} ordinary={} soulCut={} soulBurst={} before={} after={}"
                  + " ordinaryReturned={}",
              mode,
              normal,
              cut,
              burst,
              before,
              t.getHealth(),
              ordinary);
      t.discard();
    }
    // Two legitimately authorized owners share one target reservation, even with separate cores.
    advance(level, 220);
    var other =
        new net.minecraftforge.common.util.FakePlayer(
            level,
            new GameProfile(UUID.fromString("977e986c-c78b-42a1-8e4a-a6ac5d4308b1"), "SoulOther"));
    other.moveTo(.5, 150, .5, 0, 0);
    other.getInventory().selected = 0;
    AkatsukiDomainValidation.prepareBlade(other);
    ContractMasteryService.setLevel(server.createCommandSourceStack(), other, 2);
    SoulAnchorService.fixtureOwner(other);
    var shared = EntityType.ZOMBIE.create(level);
    shared.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
    shared.setHealth(200);
    shared.moveTo(3, 150, .5, 0, 0);
    level.addFreshEntity(shared);
    var first = SoulAnchorService.create(p, shared);
    var second = SoulAnchorService.create(other, shared);
    check(first != null && second != null, "separate owners can each bind one core");
    check(
        !SoulAnchorService.strike(first, other.damageSources().playerAttack(other)),
        "other player cannot hijack core");
    double maximum = SoulDamageEngine.requested(p, shared, SoulDamagePolicy.Mode.SOUL_BURST);
    var unstampedArrow = new net.minecraft.world.entity.projectile.Arrow(level, p);
    check(
        !SoulAnchorService.strike(first, p.damageSources().arrow(unstampedArrow, p)),
        "unstamped ranged damage cannot borrow a held Akatsuki");
    float hpBefore = shared.getHealth();
    check(
        SoulAnchorService.strike(first, p.damageSources().playerAttack(p)),
        "owner reserves one burst");
    check(
        !SoulAnchorService.strike(second, other.damageSources().playerAttack(other)),
        "shared target prevents multiplayer double burst");
    for (int n = 0; n < 14; n++) {
      advance(level, 1);
      if (!first.isRemoved()) SoulAnchorService.strike(first, p.damageSources().playerAttack(p));
      SoulBurstService.tick(new TickEvent.ServerTickEvent(TickEvent.Phase.END, () -> true, server));
    }
    check(
        hpBefore - shared.getHealth() <= maximum + .01,
        "high attack speed conserves one finite total");
    check(
        !SoulDamageEngine.burstReady(shared.getUUID(), level.getGameTime()),
        "other blade cannot reopen cooldown");
    shared.discard();
    var replacement = EntityType.ZOMBIE.create(level);
    replacement.setUUID(shared.getUUID());
    replacement.moveTo(3, 150, .5, 0, 0);
    check(level.addFreshEntity(replacement), "replacement fixture loaded");
    float replacementHealth = replacement.getHealth();
    second.tick();
    check(
        second.isRemoved() && replacement.getHealth() == replacementHealth,
        "same UUID replacement invalidates the old core without ghost damage");
    replacement.discard();
    LogUtils.getLogger()
        .info(
            "SOUL_MULTIPLAYER_HIGH_SPEED_PASS loss={} requested={}",
            hpBefore - shared.getHealth(),
            maximum);
    for (String id :
        List.of(
            "cataclysm:ignis",
            "legendary_monsters:cloud_golem",
            "legendary_monsters:the_obliterator",
            "goety:apostle")) {
      var key = ResourceLocation.parse(id);
      var type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getOptional(key);
      if (type.isEmpty()) {
        LogUtils.getLogger().info("SOUL_BOSS_NOT_INSTALLED {}", id);
        continue;
      }
      advance(level, 220);
      var entity = type.get().create(level);
      check(entity instanceof LivingEntity, "boss living " + id);
      var t = (LivingEntity) entity;
      t.moveTo(3, 150, .5, 0, 0);
      if (t instanceof Mob m) m.setNoAi(true);
      level.addFreshEntity(t);
      // Wake a real encounter using its public state API; never alter its cap or health
      // architecture.
      try {
        t.getClass().getMethod("setIsSleep", boolean.class).invoke(t, false);
      } catch (NoSuchMethodException ignored) {
      }
      try {
        t.getClass().getMethod("setSleep", boolean.class).invoke(t, false);
      } catch (NoSuchMethodException ignored) {
      }
      float hp = t.getHealth();
      t.hurt(p.damageSources().playerAttack(p), 100);
      double ordinary = hp - t.getHealth();
      if (id.endsWith("cloud_golem"))
        t.getClass().getField("GolemInvulnerabilityTime").setInt(t, 60);
      var s = AkatsukiDomainService.session(p);
      s.contact(level.getGameTime(), t.getUUID());
      hp = t.getHealth();
      var result = SoulDamageEngine.cut(p, t, s);
      double cut = hp - t.getHealth();
      if (id.endsWith("cloud_golem"))
        check(
            t.getClass().getField("GolemInvulnerabilityTime").getInt(t) == 60,
            "cloud timer remains exactly intact after soul read override");
      // The same entity, untouched cap and actual native soul entry.
      advance(level, 20);
      t.invulnerableTime = 0;
      var core = SoulAnchorService.create(p, t);
      double burst = 0;
      if (core != null && SoulAnchorService.strike(core, p.damageSources().playerAttack(p))) {
        hp = t.getHealth();
        for (int n = 0; n < 14; n++) {
          advance(level, 1);
          SoulBurstService.tick(
              new TickEvent.ServerTickEvent(TickEvent.Phase.END, () -> true, server));
        }
        burst = hp - t.getHealth();
      }
      LogUtils.getLogger()
          .info(
              "SOUL_REAL_BOSS_COMPARISON id={} ordinary={} cut={} burst={} health={} limits={}"
                  + " feedback={}",
              id,
              ordinary,
              cut,
              burst,
              t.getHealth(),
              DomainTargetCompatibility.inspect(t),
              result);
      if (id.startsWith("cataclysm:") || id.startsWith("legendary_monsters:"))
        check(cut > 0, "real adapted boss loses health " + id);
      if (id.endsWith("cloud_golem")) {
        for (boolean legacy : new boolean[] {true, false}) {
          var toggle =
              legacy
                  ? com.maidweapon.common.AkatsukiDomainConfig.BOSS_ADAPTERS
                  : SoulDamageConfig.KNOWN_ADAPTERS;
          boolean original = toggle.get();
          try {
            toggle.set(false);
            advance(level, 220);
            s.contact(level.getGameTime(), t.getUUID());
            hp = t.getHealth();
            var disabled = SoulDamageEngine.cut(p, t, s);
            check(
                t.getHealth() == hp
                    && t.getClass().getField("GolemInvulnerabilityTime").getInt(t) == 60,
                "either adapter switch disables the private timer privilege");
            LogUtils.getLogger()
                .info("SOUL_ADAPTER_DISABLED_PASS legacy={} feedback={}", legacy, disabled);
          } finally {
            toggle.set(original);
          }
        }
      }
      if (id.endsWith("ignis")) {
        advance(level, 20);
        var animation = t.getClass().getField("PHASE_2").get(null);
        t.getClass().getMethod("setAnimation", animation.getClass()).invoke(t, animation);
        s.contact(level.getGameTime(), t.getUUID());
        hp = t.getHealth();
        var transition = SoulDamageEngine.cut(p, t, s);
        check(t.getHealth() < hp, "phase reduction is not a blanket exclusion");
        LogUtils.getLogger()
            .info(
                "SOUL_REAL_PHASE_COMPARISON id={} before={} after={} feedback={}",
                id,
                hp,
                t.getHealth(),
                transition);
      }
      if (id.endsWith("ignis")) {
        // Explicit experimental profile: confirm the exact registered life path, once, after a
        // real canceled hurt. The default aggressive comparisons above do not use compensation.
        advance(level, 220);
        var originalStrategy = SoulDamageConfig.STRATEGY.get();
        boolean originalOverride = SoulDamageConfig.EVENT_OVERRIDE.get();
        try {
          SoulDamageConfig.STRATEGY.set(SoulDamageConfig.Strategy.UNRESTRICTED_EXPERIMENTAL);
          SoulDamageConfig.EVENT_OVERRIDE.set(false);
          cancel = t;
          s.contact(level.getGameTime(), t.getUUID());
          hp = t.getHealth();
          var correction = SoulDamageEngine.cut(p, t, s);
          check(
              correction != null
                  && correction.compensated()
                  && !correction.returned()
                  && correction.success()
                  && correction.loss() <= correction.requested() + .01,
              "one verified residual correction");
          LogUtils.getLogger()
              .info(
                  "SOUL_EXPERIMENTAL_COMPENSATION_PASS before={} after={} feedback={}",
                  hp,
                  t.getHealth(),
                  correction);
        } finally {
          cancel = null;
          SoulDamageConfig.STRATEGY.set(originalStrategy);
          SoulDamageConfig.EVENT_OVERRIDE.set(originalOverride);
        }
      }
      t.discard();
    }
    // Real hurt-path microbenchmark; excludes setup, reset-health and world-clock movement.
    advance(level, 220);
    var benchmark = EntityType.ZOMBIE.create(level);
    benchmark.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1024);
    benchmark.setHealth(1024);
    benchmark.moveTo(3, 150, .5, 0, 0);
    benchmark.setNoAi(true);
    level.addFreshEntity(benchmark);
    long[] timings = new long[200];
    int delivered = 0;
    for (int n = 0; n < 220; n++) {
      advance(level, 4);
      benchmark.setHealth(1024);
      var state = AkatsukiDomainService.session(p);
      state.contact(level.getGameTime(), benchmark.getUUID());
      long begin = System.nanoTime();
      var feedback = SoulDamageEngine.cut(p, benchmark, state);
      long elapsed = System.nanoTime() - begin;
      if (n >= 20) {
        timings[n - 20] = elapsed;
        if (feedback != null && feedback.success()) delivered++;
      }
    }
    java.util.Arrays.sort(timings);
    check(
        delivered == 200,
        "persistent domain continues delivering without permanently exhausted budget");
    LogUtils.getLogger()
        .info(
            "SOUL_HURT_PATH_BENCHMARK samples={} meanMicros={} p95Micros={} maxMicros={}"
                + " delivered={}",
            timings.length,
            java.util.Arrays.stream(timings).average().orElse(0) / 1000,
            timings[189] / 1000d,
            timings[199] / 1000d,
            delivered);
    benchmark.discard();
    p.getInventory().setItem(0, ItemStack.EMPTY);
    check(!AkatsukiDomainService.session(p).active(), "weapon switching suspends soul damage");
    check(SoulStrikeContext.current() == null, "no global attack state at end");
  }

  private static void advance(ServerLevel level, int ticks) {
    ((net.minecraft.world.level.storage.ServerLevelData) level.getLevelData())
        .setGameTime(level.getGameTime() + ticks);
  }

  private static final class Capped extends Zombie {
    boolean blocked;
    float bucket;

    Capped(ServerLevel l, boolean block) {
      super(EntityType.ZOMBIE, l);
      blocked = block;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
      if (blocked) return false;
      if (!source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
        amount = Math.min(5, Math.min(amount, 10 - bucket));
        bucket += amount;
      }
      return amount > 0 && super.hurt(source, amount);
    }
  }

  private static final class CustomHealth extends Zombie {
    float soulHealth = 200;

    CustomHealth(ServerLevel l) {
      super(EntityType.ZOMBIE, l);
    }

    @Override
    public float getHealth() {
      return soulHealth;
    }

    @Override
    public void setHealth(float hp) {
      soulHealth = Math.max(0, hp);
    }
  }

  private static void check(boolean ok, String detail) {
    if (!ok) throw new IllegalStateException(detail);
  }

  private SoulDamageValidation() {}
}
