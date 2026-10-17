package com.maidweapon.forge.system.akatsuki.provenance;

import static com.maidweapon.forge.system.akatsuki.provenance.DamageObservation.Classification.*;

import com.maidweapon.common.SoulDamageConfig;
import com.maidweapon.forge.compat.DomainTargetCompatibility;
import com.maidweapon.forge.system.akatsuki.AkatsukiDomainGrowth;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Read-only, synchronous and exception-safe. No probe attacks, HP rollback or event mutation. */
@Mod.EventBusSubscriber(modid = "maid_weapon")
public final class DamageObservationService {
  private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();
  private static final ClassValue<Boolean> VANILLA_HEALTH =
      new ClassValue<>() {
        protected Boolean computeValue(Class<?> type) {
          for (Class<?> c = type; c != null; c = c.getSuperclass())
            for (String name : new String[] {"getHealth", "m_21223_"})
              try {
                c.getDeclaredMethod(name);
                return c == LivingEntity.class;
              } catch (NoSuchMethodException ignored) {
              }
          return false;
        }
      };
  private static final Map<MinecraftServer, Long> SEQUENCES = new WeakHashMap<>();
  private static final Map<MinecraftServer, Deque<DamageObservation>> RECENT = new WeakHashMap<>();

  private static String owner(Class<?> type, String... names) {
    for (Class<?> c = type; c != null; c = c.getSuperclass())
      for (String name : names)
        try {
          c.getDeclaredMethod(name, DamageSource.class, float.class);
          return c.getName();
        } catch (NoSuchMethodException ignored) {
        }
    return "";
  }

  private static final ClassValue<String> HURT_ENTRY =
      new ClassValue<>() {
        protected String computeValue(Class<?> t) {
          return owner(t, "hurt", "m_6469_");
        }
      };
  private static final ClassValue<Boolean> VANILLA_DEFENSE =
      new ClassValue<>() {
        protected Boolean computeValue(Class<?> t) {
          return owner(t, "getDamageAfterArmorAbsorb", "m_21161_")
                  .equals(LivingEntity.class.getName())
              && owner(t, "getDamageAfterMagicAbsorb", "m_6515_")
                  .equals(LivingEntity.class.getName());
        }
      };

  public static final class Scope implements AutoCloseable {
    final Scope previous;
    final Frame f;
    final String entry;
    final boolean root;
    boolean closed;

    Scope(Scope previous, Frame f, String entry, boolean root) {
      this.previous = previous;
      this.f = f;
      this.entry = entry;
      this.root = root;
    }

    public void returned(boolean v) {
      if (root && f != null) f.returned = v;
    }

    public void failed() {
      if (f != null) f.failed = true;
    }

    public void close() {
      if (closed) return;
      closed = true;
      if (CURRENT.get() != this) {
        CURRENT.remove();
        return;
      }
      if (previous == null) CURRENT.remove();
      else CURRENT.set(previous);
      if (root && f != null) {
        double loss = Math.max(0, f.before - f.t.getHealth());
        if (previous != null && previous.f != null && previous.f.t == f.t)
          previous.f.children += loss;
        try {
          finish(f);
        } catch (RuntimeException | LinkageError e) {
          com.maidweapon.forge.compat.CompatDiagnostics.warnOnce("damage-observation-finish", e);
        }
      }
    }
  }

  private static final class Frame {
    final LivingEntity t;
    final DamageSource source;
    final DamageProvenanceResolver.Proof proof;
    final String entry, phase;
    final double expectedLimit;
    final float raw, before, absorption, offeredRaw;
    final DamageObservation.Defense defense;
    double children,
        capResult = Double.NaN,
        attack = Double.NaN,
        hurtIn = Double.NaN,
        hurtOut = Double.NaN,
        damageIn = Double.NaN,
        damageOut = Double.NaN,
        armorIn = Double.NaN,
        armorOut = Double.NaN,
        magicIn = Double.NaN,
        magicOut = Double.NaN;
    boolean capEvidence, returned, failed;

    Frame(
        LivingEntity t,
        DamageSource s,
        float raw,
        String entry,
        DamageProvenanceResolver.Proof proof) {
      this.t = t;
      source = s;
      this.raw = raw;
      this.offeredRaw = NativeAttackOrigin.offered(s, t);
      this.entry = entry;
      this.proof = proof;
      before = t.getHealth();
      absorption = t.getAbsorptionAmount();
      defense = snapshot(t, s);
      phase = DomainTargetCompatibility.inspect(t).phase();
      expectedLimit = DomainTargetCompatibility.expectedNativeLimit(t, s, raw);
    }

    double armor(double n) {
      return source.is(DamageTypeTags.BYPASSES_ARMOR)
          ? n
          : CombatRules.getDamageAfterAbsorb(
              (float) n, defense.armor(), (float) defense.toughness());
    }

    double magic(double n) {
      if (source.is(DamageTypeTags.BYPASSES_EFFECTS)) return n;
      if (!source.is(DamageTypeTags.BYPASSES_RESISTANCE))
        n *= Math.max(0, 1 - defense.resistance() * .2);
      return source.is(DamageTypeTags.BYPASSES_ENCHANTMENTS)
          ? n
          : CombatRules.getDamageAfterMagicAbsorb((float) n, defense.enchantment());
    }
  }

  private static DamageObservation.Defense snapshot(LivingEntity t, DamageSource s) {
    var r = t.getEffect(MobEffects.DAMAGE_RESISTANCE);
    return new DamageObservation.Defense(
        t.getArmorValue(),
        t.getAttributeValue(Attributes.ARMOR_TOUGHNESS),
        r == null ? 0 : r.getAmplifier() + 1,
        EnchantmentHelper.getDamageProtection(t.getArmorSlots(), s),
        t.isBlocking());
  }

  public static boolean sameNormalDefense(DamageObservation o, LivingEntity t) {
    return o.defense().equals(snapshot(t, o.source()))
        && t.getAbsorptionAmount() <= o.absorptionAfter() + .015;
  }

  public static Scope open(LivingEntity t, DamageSource source, float raw, String entry) {
    if (t.level().isClientSide || t.getServer() == null || !t.getServer().isSameThread())
      return new Scope(null, null, entry, false);
    var previous = CURRENT.get();
    boolean delegation =
        previous != null
            && previous.f != null
            && previous.f.t == t
            && previous.f.source == source
            && parent(previous.entry, entry, t);
    Frame f = null;
    try {
      if (delegation) f = previous.f;
      else {
        var proof =
            Float.isFinite(raw) && raw > 0 ? DamageProvenanceResolver.resolve(source, t) : null;
        if (proof != null || previous != null) f = new Frame(t, source, raw, entry, proof);
      }
    } catch (RuntimeException | LinkageError e) {
      if (previous != null && previous.f != null) previous.f.failed = true;
      com.maidweapon.forge.compat.CompatDiagnostics.warnOnce("damage-observation-open", e);
    }
    var scope = new Scope(previous, f, entry, !delegation);
    CURRENT.set(scope);
    return scope;
  }

  private static boolean parent(String child, String parent, LivingEntity t) {
    if (child.equals(parent)) return false;
    boolean seen = false;
    for (Class<?> c = t.getClass(); c != null; c = c.getSuperclass()) {
      if (c.getName().equals(child)) seen = true;
      if (c.getName().equals(parent)) return seen;
    }
    return false;
  }

  private static Frame matching(LivingEntity t, DamageSource s) {
    var scope = CURRENT.get();
    return scope != null && scope.f != null && scope.f.t == t && scope.f.source == s
        ? scope.f
        : null;
  }

  public static void attack(LivingEntity t, DamageSource s, float n, boolean ok) {
    var f = matching(t, s);
    if (f != null) f.attack = ok ? n : 0;
  }

  public static void hurt(LivingEntity t, DamageSource s, float in, float out) {
    var f = matching(t, s);
    if (f != null) {
      f.hurtIn = in;
      f.hurtOut = out;
    }
  }

  public static void damage(LivingEntity t, DamageSource s, float in, float out) {
    var f = matching(t, s);
    if (f != null) {
      f.damageIn = in;
      f.damageOut = out;
    }
  }

  public static void defense(LivingEntity t, DamageSource s, float in, float out, boolean armor) {
    var f = matching(t, s);
    if (f != null) {
      if (armor) {
        f.armorIn = in;
        f.armorOut = out;
      } else {
        f.magicIn = in;
        f.magicOut = out;
      }
    }
  }

  /** Called only at bytecode- and version-gated native Math.min cap sites. */
  public static float capped(float a, float b, float result) {
    try {
      var scope = CURRENT.get();
      var f = scope == null ? null : scope.f;
      if (f != null && f.proof != null && DomainTargetCompatibility.verified(f.t)) {
        double cap = DomainTargetCompatibility.inspect(f.t).hitCap();
        double raw = same(a, cap) ? b : same(b, cap) ? a : Double.NaN;
        if (cap > 0 && same(raw, f.raw) && Float.isFinite(result) && result >= 0) {
          f.capEvidence = true;
          f.capResult = f.expectedLimit;
        }
      }

    } catch (RuntimeException | LinkageError failure) {
      com.maidweapon.forge.compat.CompatDiagnostics.warnOnce("damage-cap-observation", failure);
    }
    return result;
  }

  private static boolean same(double a, double b) {
    return Double.isFinite(a)
        && Double.isFinite(b)
        && Math.abs(a - b) <= Math.max(.015, Math.abs(a) * .0001);
  }

  /**
   * Some unmodified vanilla mobs (notably Zombie) override hurt() and delegate
   * to LivingEntity. The inner observation hook reports LivingEntity as its
   * entrypoint although the declared outer entry is the exact vanilla mob.
   * Admit that known vanilla delegation only; custom subclasses and modded
   * entities remain excluded unless they supply their own verified adapter.
   */
  private static boolean nativeVanillaDelegation(LivingEntity target, String innerEntry) {
    return innerEntry.equals(LivingEntity.class.getName())
        && target.getClass().getName().startsWith("net.minecraft.world.entity.")
        && net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
            .getKey(target.getType()).getNamespace().equals("minecraft")
        && HURT_ENTRY.get(target.getClass()).startsWith("net.minecraft.world.entity.");
  }

  /**
   * Already source-/target-authenticated native provenance spans the target's
   * own hurt() and any super.hurt() delegation. Do not require an extra offered
   * amount: launched vanilla SlashBlade attacks sometimes have no such envelope.
   */
  private static boolean trustedNativeDelegation(LivingEntity target, String observedEntry,
      DamageProvenanceResolver.Proof proof) {
    if (proof == null || !proof.valid()) return false;
    for (Class<?> c = target.getClass().getSuperclass(); c != null; c = c.getSuperclass())
      if (c.getName().equals(observedEntry)) return true;
    return false;
  }

  private static void finish(Frame f) {
    if (f.proof == null) return;
    var t = f.t;
    var p = f.proof.player();
    var state = DomainTargetCompatibility.inspect(t);
    double actual = Math.max(0, f.before - t.getHealth() - f.children), effective = Double.NaN;
    var kind = UNVERIFIED_REJECTION;
    boolean valid =
        f.proof.valid()
            && t.level() == p.level()
            && !t.isRemoved()
            && p.serverLevel().getEntity(t.getUUID()) == t;
    if (!valid || f.failed) kind = UNSUPPORTED_SOURCE;
    else if (!HURT_ENTRY.get(t.getClass()).equals(f.entry)
        && !nativeVanillaDelegation(t, f.entry)
        && !trustedNativeDelegation(t, f.entry, f.proof)) kind = UNVERIFIED_REJECTION;
    else if ((!VANILLA_DEFENSE.get(t.getClass())
            && !DomainTargetCompatibility.normalDefenseVerified(t, f.source))
        || f.source.getClass() != DamageSource.class) kind = CUSTOM_HEALTH_UNKNOWN;
    else if (!VANILLA_HEALTH.get(t.getClass())
        && !DomainTargetCompatibility.observationHealthVerified(t)) kind = CUSTOM_HEALTH_UNKNOWN;
    else if (state.protectedPhase() || !f.phase.equals(state.phase())) kind = PROTECTED_PHASE;
    else if (!Float.isFinite(t.getHealth()) || actual > f.raw + .02) kind = AMBIGUOUS_CHANGE;
    else if (Double.isFinite(f.damageOut) && !same(actual, Math.min(f.before, f.damageOut)))
      kind = CUSTOM_HEALTH_UNKNOWN;
    else if (!same(f.armorOut, f.armor(f.armorIn)) || !same(f.magicOut, f.magic(f.magicIn)))
      kind = NORMAL_DEFENSE;
    else if (f.capEvidence
        && same(f.hurtIn, f.capResult)
        && same(f.armorIn, f.hurtOut)
        && same(f.magicIn, f.armorOut)
        && same(f.damageIn, Math.max(0, f.magicOut - f.absorption))) {
      effective = Math.min(f.before, Math.max(0, f.magic(f.armor(f.raw)) - f.absorption));
      kind = effective > actual + .015 ? VERIFIED_SPECIAL_CAP : NORMAL_DEFENSE;
    } else if (GenericSoulLimitEvidence.confirmed(
        Float.isFinite(f.offeredRaw) ? f.offeredRaw : f.raw, f.raw, f.hurtIn, f.hurtOut, f.armorIn,
        f.armorOut, f.magicIn, f.magicOut, f.damageIn, f.damageOut, actual)) {
      // This evidence comes from a real one-to-one native attack, not a boss-ID
      // whitelist or speculative max-health multiplier. Recompute ordinary armor
      // and resistance from the ORIGINAL uncut request.
      effective = Math.min(f.before,
          Math.max(0, f.magic(f.armor(Float.isFinite(f.offeredRaw)
              ? Math.max(f.raw, f.offeredRaw) : f.raw)) - f.absorption));
      kind = effective > actual + .015 ? OBSERVED_SPECIAL_LIMIT : NORMAL_DEFENSE;
    } else if (!same(f.hurtIn, f.hurtOut) || !same(f.damageIn, f.damageOut)) {
      kind = UNVERIFIED_REJECTION;
    } else if (Double.isFinite(f.damageOut)) {
      effective = actual;
      kind = f.absorption > 0 ? ABSORPTION : NORMAL_DEFENSE;
    }
    if (kind == CUSTOM_HEALTH_UNKNOWN
        && (SoulDamageConfig.DIAGNOSTICS.get()
            || Boolean.getBoolean("contractblade.soul.validation")))
      com.mojang.logging.LogUtils.getLogger()
          .info(
              "SOUL_OBSERVATION_BOUNDARIES target={} vanillaDefense={} verifiedDefense={}"
                  + " vanillaHealth={} verifiedHealth={} sourceClass={} actual={} damageOut={}",
              t.getClass().getName(),
              VANILLA_DEFENSE.get(t.getClass()),
              DomainTargetCompatibility.normalDefenseVerified(t, f.source),
              VANILLA_HEALTH.get(t.getClass()),
              DomainTargetCompatibility.observationHealthVerified(t),
              f.source.getClass().getName(),
              actual,
              f.damageOut);
    boolean recover =
        valid && t.isAlive()
            && (kind == VERIFIED_SPECIAL_CAP || kind == OBSERVED_SPECIAL_LIMIT)
            && Double.isFinite(effective);
    long now = p.getServer().overworld().getGameTime();
    long id = SEQUENCES.merge(p.getServer(), 1L, Long::sum);
    var o =
        new DamageObservation(
            id,
            p.getUUID(),
            t.getUUID(),
            t.getId(),
            f.proof,
            f.source,
            p.getServer(),
            t.level().dimension(),
            kind == OBSERVED_SPECIAL_LIMIT && Float.isFinite(f.offeredRaw)
                ? Math.max(f.raw, f.offeredRaw) : f.raw,
            effective,
            actual,
            f.before,
            t.getHealth(),
            f.defense,
            t.getAbsorptionAmount(),
            f.attack,
            f.hurtIn,
            f.hurtOut,
            f.damageIn,
            f.damageOut,
            f.phase,
            state.phase(),
            kind,
            f.capEvidence ? t.getClass().getName()
                : kind == OBSERVED_SPECIAL_LIMIT ? "native-observation" : "unverified",
            kind == VERIFIED_SPECIAL_CAP ? DamageObservation.Confidence.VERIFIED_NATIVE_LIMIT
                : kind == OBSERVED_SPECIAL_LIMIT
                    ? DamageObservation.Confidence.OBSERVED_NATIVE_LIMIT
                    : DamageObservation.Confidence.UNKNOWN,
            0,
            recover,
            f.returned,
            now + (long) AkatsukiDomainGrowth.parameter(p, "debt_lifetime"));
    var records = RECENT.computeIfAbsent(p.getServer(), s -> new ArrayDeque<>());
    while (!records.isEmpty() && (records.size() >= 128 || records.peekFirst().expires() <= now))
      records.removeFirst();
    records.addLast(o);
    if (SoulDamageConfig.DIAGNOSTICS.get()
        || Boolean.getBoolean("contractblade.soul.validation")
        || Boolean.getBoolean("contractblade.soulVisual.validation"))
      com.mojang.logging.LogUtils.getLogger().info("SOUL_DAMAGE_OBSERVATION {}", o);
    net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new DamageObservedEvent(o, t));
  }

  public static boolean issued(DamageObservation o, LivingEntity t) {
    return o != null
        && t != null
        && o.entityId() == t.getId()
        && o.target().equals(t.getUUID())
        && RECENT.getOrDefault(o.server(), new ArrayDeque<>()).stream().anyMatch(x -> x == o);
  }

  public static DamageObservation latest(MinecraftServer s) {
    var q = RECENT.get(s);
    return q == null ? null : q.peekLast();
  }

  public static boolean active() {
    return CURRENT.get() != null;
  }

  @SubscribeEvent
  public static void stop(net.minecraftforge.event.server.ServerStoppedEvent e) {
    RECENT.remove(e.getServer());
    SEQUENCES.remove(e.getServer());
    CURRENT.remove();
  }

  @SubscribeEvent
  public static void logout(
      net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e) {
    RECENT.values().forEach(q -> q.removeIf(o -> o.player().equals(e.getEntity().getUUID())));
  }

  private DamageObservationService() {}
}
