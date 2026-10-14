package com.maidweapon.forge.compat;

import com.maidweapon.common.AkatsukiDomainConfig;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.fml.ModList;

/** Read-only limits/phase SPI, with scoped restoration of our own hit bookkeeping. */
public final class DomainTargetCompatibility {
  public record State(double hitCap, double dpsCap, boolean protectedPhase, String phase) {
    public static State unknown(LivingEntity t) {
      return new State(0, 0, t.isInvulnerable(), "generic");
    }
  }

  public interface Adapter {
    boolean supports(LivingEntity target);

    State inspect(LivingEntity target) throws ReflectiveOperationException;
  }

  private static final Map<String, Adapter> ADAPTERS = new LinkedHashMap<>();

  public static synchronized void register(String id, Adapter adapter) {
    if (ADAPTERS.putIfAbsent(id, Objects.requireNonNull(adapter)) != null)
      throw new IllegalArgumentException("Duplicate domain adapter " + id);
  }

  private static boolean version(String mod, String prefix) {
    return ModList.get()
        .getModContainerById(mod)
        .map(c -> c.getModInfo().getVersion().toString().startsWith(prefix))
        .orElse(false);
  }

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
            return version("legendarymonsters", "2.1.15")
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
            return new State(number(t, "damageCap"), 0, protectedPhase, "lm:" + state);
          }
        });
    register(
        "maid_weapon:cataclysm",
        new Adapter() {
          public boolean supports(LivingEntity t) {
            return version("cataclysm", "3.31")
                && family(
                    t,
                    "com.github.L_Ender.cataclysm.entity.AnimationMonster.BossMonsters.LLibrary_Boss_Monster");
          }

          public State inspect(LivingEntity t) throws ReflectiveOperationException {
            boolean protectedPhase = t.isInvulnerable();
            String phase = "cataclysm";
            if (t.getClass().getSimpleName().equals("Ignis_Entity")) {
              Object animation = read(t, "getAnimation");
              for (String name : List.of("PHASE_2", "PHASE_3", "STRIKE", "ULTIMATE_ATTACK"))
                protectedPhase |= animation == t.getClass().getField(name).get(null);
              phase += ":" + read(t, "getBossPhase") + ":" + System.identityHashCode(animation);
            }
            return new State(number(t, "DamageCap"), number(t, "DpsCap"), protectedPhase, phase);
          }
        });
  }

  public static State inspect(LivingEntity target) {
    if (!AkatsukiDomainConfig.BOSS_ADAPTERS.get()) return State.unknown(target);
    for (var e : ADAPTERS.entrySet())
      try {
        if (e.getValue().supports(target)) return e.getValue().inspect(target);
      } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
        CompatDiagnostics.warnOnce("domain-target:" + e.getKey(), failure);
        return new State(0, 0, true, "unsupported-adapter");
      }
    return State.unknown(target);
  }

  public interface HitWindow extends AutoCloseable {
    default void before() {}

    @Override
    void close();
  }

  /**
   * Only the audited versions' cooldown/bucket fields; health and phase state are never written.
   */
  public static HitWindow hitWindow(LivingEntity target, boolean absolute) {
    var fields = new LinkedHashMap<Field, Object>();
    var names = new ArrayList<String>();
    if (AkatsukiDomainConfig.BOSS_ADAPTERS.get()
        && version("legendarymonsters", "2.1.15")
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
        && family(
            target,
            "com.github.L_Ender.cataclysm.entity.AnimationMonster.BossMonsters.LLibrary_Boss_Monster"))
      names.add("damageBucket");
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
      public void before() {
        // Audited Cloud Golem rejects even strong tags before Forge events. Temporarily
        // admit ONLY this reserved absolute pulse; sleep/transition was checked above.
        if (absolute)
          fields.forEach(
              (field, value) -> {
                if (field.getName().equals("GolemInvulnerabilityTime"))
                  try {
                    field.setInt(target, 0);
                  } catch (IllegalAccessException failure) {
                    throw new IllegalStateException(failure);
                  }
              });
      }

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
