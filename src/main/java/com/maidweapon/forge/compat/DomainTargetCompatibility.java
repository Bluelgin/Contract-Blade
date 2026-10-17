package com.maidweapon.forge.compat;

import com.maidweapon.common.AkatsukiDomainConfig;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.fml.ModList;

/** One versioned limits/phase/health SPI, with scoped restoration of hit bookkeeping. */
public final class DomainTargetCompatibility {
  public record State(double hitCap, double dpsCap, boolean protectedPhase, String phase) {
    public static State unknown(LivingEntity t) {
      return new State(0, 0, t.isInvulnerable(), "generic");
    }
  }

  public interface Adapter extends BossLimitAdapter {
    boolean supports(LivingEntity target);

    State inspect(LivingEntity target) throws ReflectiveOperationException;
  }

  private static final Map<String, Adapter> ADAPTERS = new LinkedHashMap<>();

  public static synchronized void register(String id, Adapter adapter) {
    if (ADAPTERS.putIfAbsent(id, Objects.requireNonNull(adapter)) != null)
      throw new IllegalArgumentException("Duplicate domain adapter " + id);
  }

  public static boolean version(String mod, String prefix) {
    if (!ModList.get().isLoaded(mod)) return false;
    String detected =
        VERSIONS.computeIfAbsent(
            mod,
            id -> {
              var info = ModList.get().getModContainerById(id).orElseThrow().getModInfo();
              String declared = info.getVersion().toString();
              // This mod hardcodes Minecraft's version in mods.toml; its release version is in the
              // manifest.
              if (!id.equals("legendary_monsters") || !declared.equals("1.20.1")) return declared;
              try (var input =
                  java.nio.file.Files.newInputStream(
                      info.getOwningFile().getFile().findResource("META-INF", "MANIFEST.MF"))) {
                String release =
                    new java.util.jar.Manifest(input)
                        .getMainAttributes()
                        .getValue("Implementation-Version");
                return release == null ? "unknown" : release;
              } catch (java.io.IOException | RuntimeException failure) {
                CompatDiagnostics.warnOnce("domain-target-version:" + id, failure);
                return "unknown";
              }
            });
    return detected.equals(prefix) || detected.startsWith(prefix + " ");
  }

  private static final Map<String, String> VERSIONS = new HashMap<>();

  private static final ClassValue<Map<String, Method>> METHODS =
      new ClassValue<>() {
        @Override
        protected Map<String, Method> computeValue(Class<?> type) {
          var result = new HashMap<String, Method>();
          for (var m : type.getMethods())
            if (m.getParameterCount() == 0) result.put(m.getName(), m);
          return result;
        }
      };

  private static Object read(LivingEntity t, String method) throws ReflectiveOperationException {
    var m = METHODS.get(t.getClass()).get(method);
    if (m == null) throw new NoSuchMethodException(method);
    return m.invoke(t);
  }

  private static boolean yes(LivingEntity t, String method) throws ReflectiveOperationException {
    return Boolean.TRUE.equals(read(t, method));
  }

  private static double number(LivingEntity t, String method) throws ReflectiveOperationException {
    double n = ((Number) read(t, method)).doubleValue();
    return Double.isFinite(n) && n > 0 && n <= 10000 ? n : 0;
  }

  private static boolean family(LivingEntity t, String name) {
    for (Class<?> c = t.getClass(); c != null; c = c.getSuperclass())
      if (c.getName().equals(name)) return true;
    return false;
  }

  public static void bootstrap() {
    register(
        "maid_weapon:legendary_monsters",
        new Adapter() {
          public boolean supports(LivingEntity t) {
            return version("legendary_monsters", "2.1.15")
                && family(
                    t,
                    "net.miauczel.legendary_monsters.entity.AnimatedMonster.OriginClasses.IAnimatedBoss");
          }

          public State inspect(LivingEntity t) throws ReflectiveOperationException {
            int state = ((Number) read(t, "getAttackState")).intValue();
            boolean protectedPhase = t.isInvulnerable() || t.isVehicle();
            String name = t.getClass().getSimpleName();
            if (name.equals("Cloud_GolemEntity"))
              protectedPhase |= yes(t, "isSleep") || state == 13 || state == 21;
            if (name.equals("TheObliteratorEntity"))
              protectedPhase |=
                  Set.of(1, 2, 19, 40, 48, 52).contains(state)
                      || yes(t, "isParrying")
                      || yes(t, "canParry")
                      || yes(t, "canReduceDamageDuringPhaseTransition");
            String phase =
                name.equals("Cloud_GolemEntity")
                    ? String.valueOf(read(t, "isAngry"))
                    : name.equals("TheObliteratorEntity")
                        ? String.valueOf(read(t, "getPhase"))
                        : "generic";
            return new State(number(t, "damageCap"), 0, protectedPhase, "lm:" + phase);
          }

          public boolean initializing(LivingEntity t) throws ReflectiveOperationException {
            if (t.getClass().getSimpleName().equals("Cloud_GolemEntity"))
              return ((Number) read(t, "getAttackState")).intValue() == 21;
            if (t.getClass().getSimpleName().equals("TheObliteratorEntity"))
              return Set.of(1, 2, 19, 40, 48)
                  .contains(((Number) read(t, "getAttackState")).intValue());
            return t.isRemoved();
          }

          public boolean compensate(
              LivingEntity t, net.minecraft.world.damagesource.DamageSource source, float deficit)
              throws ReflectiveOperationException {
            // Audited cumulative-health API also updates vanilla synchronization and death
            // handling.
            if (!Set.of(
                        "net.miauczel.legendary_monsters.entity.AnimatedMonster.IAnimatedBoss.CloudGolem.Cloud_GolemEntity",
                        "net.miauczel.legendary_monsters.entity.AnimatedMonster.IAnimatedBoss.TheObliterator.TheObliteratorEntity")
                    .contains(t.getClass().getName())
                || initializing(t)) return false;
            t.getClass()
                .getMethod(
                    "addDamage", float.class, net.minecraft.world.damagesource.DamageSource.class)
                .invoke(t, deficit, source);
            return true;
          }
        });
    register(
        "maid_weapon:goety",
        new Adapter() {
          public boolean supports(LivingEntity t) {
            return version("goety", "2.5.58.4")
                && family(t, "com.Polarice3.Goety.common.entities.boss.Apostle");
          }

          public State inspect(LivingEntity t) throws ReflectiveOperationException {
            var config = Class.forName("com.Polarice3.Goety.config.AttributesConfig");
            Object value = config.getField("ApostleDamageCap").get(null);
            double cap = ((Number) value.getClass().getMethod("get").invoke(value)).doubleValue();
            return new State(
                cap,
                0,
                yes(t, "isSettingUpSecond") || !ordinaryApostle(t),
                "goety:" + read(t, "isSecondPhase") + ":" + ordinaryApostle(t));
          }

          public boolean initializing(LivingEntity t) throws ReflectiveOperationException {
            return yes(t, "isSettingUpSecond");
          }
        });
    register(
        "maid_weapon:cataclysm",
        new Adapter() {
          public boolean supports(LivingEntity t) {
            return version("cataclysm", "3.31")
                && (family(t,
                      "com.github.L_Ender.cataclysm.entity.AnimationMonster.BossMonsters.LLibrary_Boss_Monster")
                    || family(t,
                      "com.github.L_Ender.cataclysm.entity.InternalAnimationMonster.IABossMonsters.IABoss_monster"));
          }

          public State inspect(LivingEntity t) throws ReflectiveOperationException {
            boolean protectedPhase = t.isInvulnerable();
            String phase = "cataclysm";
            if (t.getClass().getName().equals(
                "com.github.L_Ender.cataclysm.entity.InternalAnimationMonster.IABossMonsters"
                    + ".NewNetherite_Monstrosity.Netherite_Monstrosity_Entity")) {
              // Native rage transition: exact attack state 4 explicitly rejects damage.
              int attackState = ((Number) read(t, "getAttackState")).intValue();
              protectedPhase |= attackState == 4;
              phase += ":monstrosity:" + (attackState == 4 ? "transition" : "normal");
            }
            if (t.getClass().getSimpleName().equals("Ignis_Entity")) {
              Object animation = read(t, "getAnimation");
              for (String name : List.of("PHASE_2", "PHASE_3", "STRIKE", "ULTIMATE_ATTACK"))
                protectedPhase |= animation == t.getClass().getField(name).get(null);
              phase +=
                  ":"
                      + read(t, "getBossPhase")
                      + ":"
                      + read(t, "getIsBlocking")
                      + ":"
                      + read(t, "getIsShield")
                      + ":"
                      + read(t, "getIsShieldBreak");
            }
            return new State(number(t, "DamageCap"), number(t, "DpsCap"), protectedPhase, phase);
          }

          public double expectedNativeLimit(
              LivingEntity t,
              net.minecraft.world.damagesource.DamageSource source,
              float raw,
              double cap)
              throws ReflectiveOperationException {
            var attacker = source.getEntity();
            double range = ((Number) read(t, "RangeLimit")).doubleValue();
            if (attacker != null && t.distanceToSqr(attacker) > range * range) return Double.NaN;
            double value = Math.min(raw, cap);
            if (!source.is(
                net.minecraft.tags.TagKey.create(
                    net.minecraft.core.registries.Registries.DAMAGE_TYPE,
                    net.minecraft.resources.ResourceLocation.parse(
                        "cataclysm:bypasses_hurt_time")))) {
              for (Class<?> c = t.getClass(); c != null; c = c.getSuperclass())
                try {
                  var f = c.getDeclaredField("damageBucket");
                  if (f.getType() != float.class) return Double.NaN;
                  f.setAccessible(true);
                  float bucket = f.getFloat(t);
                  return bucket + value > cap ? (cap - bucket > 0 ? cap - bucket : .1) : value;
                } catch (NoSuchFieldException ignored) {
                }
              return Double.NaN;
            }
            return value;
          }

          public boolean compensate(
              LivingEntity t, net.minecraft.world.damagesource.DamageSource source, float deficit) {
            if (!t.getClass()
                .getName()
                .equals(
                    "com.github.L_Ender.cataclysm.entity.AnimationMonster.BossMonsters.Ignis_Entity"))
              return false;
            t.setHealth(Math.max(0, t.getHealth() - deficit));
            if (t.getHealth() <= 0) t.die(source);
            return true;
          }
        });
  }

  public static State inspect(LivingEntity target) {
    if (!AkatsukiDomainConfig.BOSS_ADAPTERS.get()
        || !com.maidweapon.common.SoulDamageConfig.KNOWN_ADAPTERS.get())
      return State.unknown(target);
    for (var e : ADAPTERS.entrySet())
      try {
        if (e.getValue().supports(target)) return e.getValue().inspect(target);
      } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
        CompatDiagnostics.warnOnce("domain-target:" + e.getKey(), failure);
        return new State(0, 0, true, "unsupported-adapter");
      }
    return State.unknown(target);
  }

  public static double expectedNativeLimit(
      LivingEntity target, net.minecraft.world.damagesource.DamageSource source, float raw) {
    if (!verified(target)) return Double.NaN;
    for (var a : ADAPTERS.values())
      try {
        if (a.supports(target))
          return a.expectedNativeLimit(target, source, raw, inspect(target).hitCap());
      } catch (ReflectiveOperationException | RuntimeException e) {
        CompatDiagnostics.warnOnce("native-limit-evidence", e);
        return Double.NaN;
      }
    return Double.NaN;
  }

  public static boolean observationHealthVerified(LivingEntity t) {
    return ordinaryApostle(t)
        || version("legendary_monsters", "2.1.15")
            && Set.of(
                    "net.miauczel.legendary_monsters.entity.AnimatedMonster.IAnimatedBoss.CloudGolem.Cloud_GolemEntity",
                    "net.miauczel.legendary_monsters.entity.AnimatedMonster.IAnimatedBoss.TheObliterator.TheObliteratorEntity")
                .contains(t.getClass().getName());
  }

  public static boolean ordinaryApostle(LivingEntity t) {
    if (!t.getClass().getName().equals("com.Polarice3.Goety.common.entities.boss.Apostle")
        || !version("goety", "2.5.58.4")) return false;
    if (ModList.get().isLoaded("revelationfix") && !version("revelationfix", "4.6")) return false;
    if (!ModList.get().isLoaded("goety_revelation")) return true;
    if (!version("goety_revelation", "2.3.4")) return false;
    try {
      return Boolean.FALSE.equals(read(t, "allTitlesApostle_1_20_1$isApollyon"));
    } catch (ReflectiveOperationException | RuntimeException e) {
      return false;
    }
  }

  public static boolean normalDefenseVerified(
      LivingEntity t, net.minecraft.world.damagesource.DamageSource s) {
    return ordinaryApostle(t)
        && s.getEntity() != t
        && !s.is(net.minecraft.tags.DamageTypeTags.WITCH_RESISTANT_TO);
  }

  public static boolean verified(LivingEntity target) {
    if (!AkatsukiDomainConfig.BOSS_ADAPTERS.get()
        || !com.maidweapon.common.SoulDamageConfig.KNOWN_ADAPTERS.get()) return false;
    return ADAPTERS.values().stream().anyMatch(a -> a.supports(target));
  }

  public static boolean initializing(LivingEntity target) {
    if (!AkatsukiDomainConfig.BOSS_ADAPTERS.get()
        || !com.maidweapon.common.SoulDamageConfig.KNOWN_ADAPTERS.get()) return false;
    for (var a : ADAPTERS.values())
      try {
        if (a.supports(target)) return a.initializing(target);
      } catch (ReflectiveOperationException | RuntimeException failure) {
        CompatDiagnostics.warnOnce("soul-initialization", failure);
        return true;
      }
    return false;
  }

  public static boolean compensate(
      LivingEntity target, net.minecraft.world.damagesource.DamageSource source, float deficit) {
    if (!AkatsukiDomainConfig.BOSS_ADAPTERS.get()
        || !com.maidweapon.common.SoulDamageConfig.KNOWN_ADAPTERS.get()
        || !target.isAlive()
        || target.isRemoved()
        || !Float.isFinite(deficit)
        || deficit <= 0) return false;
    for (var a : ADAPTERS.values())
      try {
        if (a.supports(target)) return a.compensate(target, source, deficit);
      } catch (ReflectiveOperationException | RuntimeException failure) {
        CompatDiagnostics.warnOnce("soul-life-compensation", failure);
        return false;
      }
    return false;
  }

  public interface HitWindow extends AutoCloseable {
    @Override
    void close();
  }

  /**
   * Only the audited versions' cooldown/bucket fields; health and phase state are never written.
   */
  public static HitWindow hitWindow(LivingEntity target, boolean legacyAbsolute) {
    var fields = new LinkedHashMap<Field, Object>();
    var names = new ArrayList<String>();
    if (AkatsukiDomainConfig.BOSS_ADAPTERS.get()
        && version("legendary_monsters", "2.1.15")
        && family(
            target,
            "net.miauczel.legendary_monsters.entity.AnimatedMonster.OriginClasses.IAnimatedBoss")) {
      names.add("hurtCD");
      if (target.getClass().getSimpleName().equals("Cloud_GolemEntity"))
        names.add("GolemInvulnerabilityTime");
      if (target.getClass().getSimpleName().equals("TheObliteratorEntity"))
        names.add("InvulnerabilityTime");
    }
    if (AkatsukiDomainConfig.BOSS_ADAPTERS.get()
        && version("cataclysm", "3.31")
        && (family(target,
                "com.github.L_Ender.cataclysm.entity.AnimationMonster.BossMonsters.LLibrary_Boss_Monster")
            || family(target,
                "com.github.L_Ender.cataclysm.entity.InternalAnimationMonster.IABossMonsters.IABoss_monster")))
      names.add("damageBucket");
    if (AkatsukiDomainConfig.BOSS_ADAPTERS.get()
        && com.maidweapon.common.SoulDamageConfig.KNOWN_ADAPTERS.get()
        && version("goety", "2.5.58.4")
        && family(target, "com.Polarice3.Goety.common.entities.boss.Apostle")) {
      names.add("moddedInvul");
      names.add("obsidianInvul");
    }
    try {
      for (String name : names) {
        Field found = null;
        for (Class<?> c = target.getClass(); c != null && found == null; c = c.getSuperclass())
          try {
            found = c.getDeclaredField(name);
          } catch (NoSuchFieldException ignored) {
          }
        if (found == null) throw new NoSuchFieldException(name);
        if (found.getType() != int.class && found.getType() != float.class)
          throw new IllegalStateException("Unsupported hit bookkeeping field " + name);
        found.setAccessible(true);
        fields.put(found, found.get(target));
      }
    } catch (ReflectiveOperationException | RuntimeException failure) {
      CompatDiagnostics.warnOnce("domain-boss-hit-window", failure);
      return null;
    }
    return new HitWindow() {
      @Override
      public void close() {
        fields.forEach(
            (field, value) -> {
              try {
                field.set(target, value);
              } catch (IllegalAccessException failure) {
                CompatDiagnostics.warnOnce("domain-boss-hit-window-restore", failure);
              }
            });
      }
    };
  }

  private DomainTargetCompatibility() {}
}
