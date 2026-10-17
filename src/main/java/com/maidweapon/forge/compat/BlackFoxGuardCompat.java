package com.maidweapon.forge.compat;

import com.maidweapon.forge.system.fox.challenge.BlackFoxCombatant;
import com.maidweapon.forge.system.fox.challenge.BlackFoxEncounters;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import java.lang.reflect.Method;

/** Runs the real native guard before native targeting applies stun/knockback. Never posts a fake hit. */
public final class BlackFoxGuardCompat {
    public enum Result { NONE, BLOCK, JUST }
    private static final ThreadLocal<Attempt> CURRENT = new ThreadLocal<>();
    private static boolean hooked;
    private static final class Attempt {
        final LivingAttackEvent event;
        boolean just;
        Attempt(LivingAttackEvent event) { this.event = event; }
    }
    public static void installed() { hooked = true; }
    public static boolean available() { return SlashBladeCompat.isLoaded() && hooked; }
    /** Called only from Guard's successful Just Guard branch, not from arbitrary event cancellation. */
    public static void just(LivingAttackEvent event) {
        var attempt = CURRENT.get();
        if (attempt != null && attempt.event == event && event.isCanceled()) attempt.just = true;
    }
    public static Result defend(ServerPlayer player, BlackFoxCombatant boss, Entity incoming) {
        if (!available() || CURRENT.get() != null || !BlackFoxEncounters.registered(boss)
                || !boss.challenger().equals(player.getUUID()) || !player.isAlive()
                || player.level() != boss.body().level() || !SlashBladeCompat.isSlashBlade(player.getMainHandItem())) return Result.NONE;
        var source = incoming instanceof Projectile projectile && !BlackFoxSlashCompat.nativeSlash(incoming)
                && !(incoming instanceof com.maidweapon.forge.entity.BlackFoxMinorCutEntity)
                ? player.damageSources().indirectMagic(projectile, boss.body())
                : player.damageSources().mobAttack(boss.body());
        var attempt = new Attempt(new LivingAttackEvent(player, source, 1));
        CURRENT.set(attempt);
        try {
            Api.handle.invoke(Api.guard, attempt.event);
            return attempt.just ? Result.JUST : attempt.event.isCanceled() ? Result.BLOCK : Result.NONE;
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Native Black Fox guard bridge", error);
        } finally { CURRENT.remove(); }
    }
    private static final class Api {
        static final Object guard;
        static final Method handle;
        static {
            try {
                var type = Class.forName("mods.flammpfeil.slashblade.ability.Guard");
                guard = type.getMethod("getInstance").invoke(null);
                handle = type.getMethod("onLivingAttack", LivingAttackEvent.class);
            } catch (ReflectiveOperationException error) { throw new ExceptionInInitializerError(error); }
        }
    }
    private BlackFoxGuardCompat() { }
}
