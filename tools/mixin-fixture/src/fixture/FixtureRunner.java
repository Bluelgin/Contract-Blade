package fixture;

import com.llamalad7.mixinextras.MixinExtrasBootstrap;
import java.lang.reflect.*;
import java.nio.file.*;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.*;

/** Runs the actual Mixin 0.8.5 / MixinExtras 0.5.3 transformer, without Minecraft. */
public final class FixtureRunner {
  public static void main(String[] args) throws Exception {
    MixinBootstrap.init();
    var transformer = FixtureService.instance.transformer();
    MixinExtrasBootstrap.init();
    Mixins.addConfiguration("fixture.mixins.json");
    var phase = MixinEnvironment.class.getDeclaredMethod("gotoPhase", MixinEnvironment.Phase.class);
    phase.setAccessible(true);
    phase.invoke(null, MixinEnvironment.Phase.DEFAULT);
    MixinEnvironment.getCurrentEnvironment().setSide(MixinEnvironment.Side.SERVER);
    var loader =
        new ClassLoader(FixtureRunner.class.getClassLoader()) {
          @Override
          protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!name.startsWith("net.minecraft.") && !name.contains("DamageObservationService"))
              return super.loadClass(name, resolve);
            var loaded = findLoadedClass(name);
            if (loaded != null) return loaded;
            try (var in = getResourceAsStream(name.replace('.', '/') + ".class")) {
              byte[] bytes =
                  in == null
                      ? transformer.generateClass(MixinEnvironment.getCurrentEnvironment(), name)
                      : transformer.transformClassBytes(name, name, in.readAllBytes());
              if (bytes == null) throw new ClassNotFoundException(name);
              var result = defineClass(name, bytes, 0, bytes.length);
              if (resolve) resolveClass(result);
              return result;
            } catch (java.io.IOException e) {
              throw new ClassNotFoundException(name, e);
            }
          }
        };
    Class<?> target = loader.loadClass("net.minecraft.world.entity.LivingEntity");
    Class<?> source = loader.loadClass("net.minecraft.world.damagesource.DamageSource");
    Class<?> sink =
        loader.loadClass(
            "com.maidweapon.forge.system.akatsuki.provenance.DamageObservationService");
    Object entity = target.getConstructor().newInstance(),
        damage = source.getConstructor().newInstance();
    Method hurt = target.getMethod(args[0], source, float.class);
    check(Boolean.TRUE.equals(hurt.invoke(entity, damage, 20f)), "hurt result");
    check(target.getField("health").getFloat(entity) == 92f, "original damage result");
    for (String counter : new String[] {"opened", "closed", "returned", "armor", "magic"})
      check(sink.getField(counter).getInt(null) == 1, counter);
    check(sink.getField("armorIn").getFloat(null) == 20f, "armor input");
    check(sink.getField("armorOut").getFloat(null) == 16f, "armor output");
    check(sink.getField("magicIn").getFloat(null) == 16f, "magic input");
    check(sink.getField("magicOut").getFloat(null) == 8f, "magic output");
    check(
        target.getMethod("helpers", source).invoke(entity, damage).equals(777f), "overload result");
    check(sink.getField("opened").getInt(null) == 1, "overload must not open observation");
    check(sink.getField("armor").getInt(null) == 1, "overload must not report armor");
    check(sink.getField("magic").getInt(null) == 1, "overload must not report magic");
    try {
      hurt.invoke(entity, damage, -1f);
      throw new AssertionError("exception swallowed");
    } catch (InvocationTargetException e) {
      check(e.getCause() instanceof IllegalArgumentException, "original exception");
    }
    check(sink.getField("failed").getInt(null) == 1, "failed scope");
    check(sink.getField("depth").getInt(null) == 0, "scope cleanup");
    check(sink.getField("closed").getInt(null) == 2, "exception closes scope");
    hurt.invoke(entity, damage, 40f);
    check(sink.getField("opened").getInt(null) == 4, "nested scopes");
    check(sink.getField("closed").getInt(null) == 4, "nested cleanup");
    check(sink.getField("depth").getInt(null) == 0, "nested depth");
    System.out.println("MIXIN_OVERLOAD_PASS " + args[0]);
  }

  private static void check(boolean value, String message) {
    if (!value) throw new AssertionError(message);
  }
}
