package com.maidweapon.forge.system.fox;

import com.maidweapon.forge.compat.CompatDiagnostics;
import com.maidweapon.forge.compat.SlashBladeCompat;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import org.joml.Vector3f;

/** Short native slash visuals with no shooter, no damage and no launch-credit authority. */
public final class WhiteFoxVisualBridge {
  public enum Cue {
    GUARD,
    CLEANSE,
    HEAL,
    EDGE,
    CRISIS,
    OATH
  }

  public static void show(LivingEntity actor, Cue cue) {
    if (!(actor.level() instanceof ServerLevel level)) return;
    try {
      if (SlashBladeCompat.isLoaded()) {
        var type =
            net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(
                net.minecraft.resources.ResourceLocation.parse("slashblade:slash_effect"));
        var visual = type.create(level);
        if (visual instanceof Projectile projectile) {
          // Configure before publication. No DoSlashEvent, shooter, damage or launch stamp.
          projectile.setOwner(null);
          visual.setPos(actor.getX(), actor.getY() + actor.getEyeHeight() * .75, actor.getZ());
          visual.setYRot(actor.getYRot());
          visual.getClass().getMethod("setDamage", double.class).invoke(visual, 0d);
          visual
              .getClass()
              .getMethod("setColor", int.class)
              .invoke(visual, cue == Cue.OATH ? 0xFFF2BE : 0xC7F4FF);
          visual.getClass().getMethod("setLifetime", int.class).invoke(visual, 6);
          visual
              .getClass()
              .getMethod("setBaseSize", float.class)
              .invoke(visual, cue == Cue.OATH || cue == Cue.CRISIS ? 1.1f : .6f);
          visual
              .getClass()
              .getMethod("setRotationRoll", float.class)
              .invoke(visual, cue == Cue.OATH || cue == Cue.CRISIS ? 0f : 60f);
          visual.getClass().getMethod("setMute", boolean.class).invoke(visual, true);
          level.addFreshEntity(visual);
        }
      }
    } catch (ReflectiveOperationException | RuntimeException failure) {
      CompatDiagnostics.warnOnce("white_fox_visual", failure);
    }
    // Clear feedback even if the optional native bridge is unavailable. No custom assets/renderer.
    level.sendParticles(
        new DustParticleOptions(new Vector3f(.8f, .96f, 1f), 1f),
        actor.getX(),
        actor.getY() + 1,
        actor.getZ(),
        cue == Cue.OATH ? 24 : 8,
        .5,
        .5,
        .5,
        0);
    if (cue == Cue.HEAL)
      level.sendParticles(
          ParticleTypes.HEART, actor.getX(), actor.getY() + 1.3, actor.getZ(), 2, .25, .2, .25, 0);
  }

  private WhiteFoxVisualBridge() {}
}
