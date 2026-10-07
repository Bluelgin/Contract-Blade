package com.maidweapon.forge.system.fox.challenge;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Small, local feedback; no camera/network spam and no global time freezing. */
public final class BlackFoxEffects {
    public static final DustParticleOptions POLLUTION = new DustParticleOptions(new Vector3f(0.45f, 0.12f, 0.7f), 1.2f);
    public static void tell(LivingEntity boss, boolean flash) {
        var level = (ServerLevel) boss.level();
        level.sendParticles(flash ? ParticleTypes.END_ROD : POLLUTION, boss.getX(), boss.getY() + 1.25,
                boss.getZ(), flash ? 5 : 9, .22, .15, .22, .025);
        level.playSound(null, boss.blockPosition(), SoundEvents.TRIDENT_RETURN, SoundSource.HOSTILE, .55f, flash ? 1.5f : .7f);
    }
    public static void contact(LivingEntity boss, LivingEntity attacker, boolean clash) {
        var level = (ServerLevel) boss.level();
        Vec3 at = boss.position().lerp(attacker.position(), .5).add(0, 1.1, 0);
        level.sendParticles(com.maidweapon.forge.compat.fox.BlackFoxBossCompat.parryParticle(),
                at.x, at.y, at.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, clash ? 28 : 16, .22, .15, .22, .22);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, clash ? 24 : 12, .12, .12, .12, .15);
        if (clash) level.sendParticles(POLLUTION, at.x, at.y, at.z, 22, .4, .5, .4, .03);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, clash ? .8f : .5f, clash ? 1.8f : 1.4f);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.TRIDENT_HIT_GROUND, SoundSource.HOSTILE, clash ? .8f : .35f, clash ? 1.4f : .8f);
    }
    public static void line(ServerLevel level, Vec3 from, Vec3 to, boolean warning) {
        int points = 18;
        for (int i = 0; i <= points; i++) {
            Vec3 point = from.lerp(to, i / (double) points);
            level.sendParticles(warning ? POLLUTION : ParticleTypes.SOUL_FIRE_FLAME,
                    point.x, point.y + .18, point.z, 1, 0, 0, 0, 0);
        }
    }
    public static void blink(LivingEntity boss, Vec3 destination, boolean arriving) {
        var level = (ServerLevel) boss.level();
        level.sendParticles(POLLUTION, boss.getX(), boss.getY() + 1, boss.getZ(),
                arriving ? 22 : 12, .3, .6, .3, .015);
        for (int i = 0; i < 12; i++) {
            double angle = i * Math.PI / 6;
            level.sendParticles(POLLUTION, destination.x + Math.cos(angle) * .6, destination.y + .15,
                    destination.z + Math.sin(angle) * .6, 1, 0, 0, 0, 0);
        }
        if (arriving) level.playSound(null, destination.x, destination.y, destination.z,
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, .4f, 1.3f);
    }
    public static void breakMomentum(LivingEntity boss) {
        var level = (ServerLevel) boss.level();
        level.sendParticles(POLLUTION, boss.getX(), boss.getY() + 1, boss.getZ(), 24, .45, .6, .45, .09);
        level.playSound(null, boss.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, .65f, .7f);
    }
    private BlackFoxEffects() { }
}
