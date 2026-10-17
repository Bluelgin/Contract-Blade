package com.maidweapon.forge.compat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import java.lang.reflect.Method;

/** Native sword visual, flight and hit implementation. Impact permission is scoped by the encounter. */
public final class BlackFoxPhantomCompat {
    private static Method color, damage, hit, noClip;
    private static void api() throws ReflectiveOperationException {
        if (hit != null) return;
        var type = Class.forName("mods.flammpfeil.slashblade.entity.EntityAbstractSummonedSword");
        // Publish the ready sentinel only after every optional-provider lookup succeeds.
        color = type.getMethod("setColor", int.class); damage = type.getMethod("setDamage", double.class);
        noClip = type.getMethod("setNoClip", boolean.class);
        hit = type.getMethod("doForceHitEntity", Entity.class);
    }
    public static Entity spawn(LivingEntity owner, Vec3 at, Vec3 direction) {
        return spawn(owner, at, direction, false, .65f);
    }
    public static Entity spawn(LivingEntity owner, Vec3 at, Vec3 direction, boolean blade, float speed) {
        try {
            api();
            var entity = (Projectile) BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(blade
                    ? "slashblade:drive" : "slashblade:abstract_summoned_sword")).create(owner.level());
            if (entity == null) throw new IllegalStateException("Native phantom sword unavailable");
            entity.setOwner(owner); entity.setPos(at);
            if (owner instanceof com.maidweapon.forge.system.fox.challenge.BlackFoxCombatant boss)
                com.maidweapon.forge.system.fox.challenge.BlackFoxAttackOrigin.stamp(entity, boss.combat().fight());
            color.invoke(entity, 0xB04CE5);
            damage.invoke(entity, owner instanceof com.maidweapon.forge.system.fox.challenge.BlackFoxCombatant
                    ? blade ? com.maidweapon.forge.system.fox.challenge.BlackFoxDamageTuning.PHANTOM_BLADE
                            : com.maidweapon.forge.system.fox.challenge.BlackFoxDamageTuning.PHANTOM_SWORD
                    : blade ? 8.0 : 5.0);
            if (blade) {
                entity.getClass().getMethod("setLifetime", float.class).invoke(entity, 60f);
                entity.getClass().getMethod("setBaseSize", float.class).invoke(entity, 1.15f);
            }
            // Vanilla virtual calls are remapped for packaged Forge; reflection on "shoot" is not.
            entity.shoot(direction.x, direction.y, direction.z, speed == 0 ? 1f : speed, 0f);
            if (speed == 0) { entity.setDeltaMovement(Vec3.ZERO); entity.setNoGravity(true); noClip.invoke(entity, true); }
            owner.level().addFreshEntity(entity);
            return entity;
        } catch (ReflectiveOperationException error) { throw new IllegalStateException("Native Black Fox phantom sword", error); }
    }
    public static void impact(Entity sword, Entity target) {
        try { api(); hit.invoke(sword, target); }
        catch (ReflectiveOperationException error) { throw new IllegalStateException("Native phantom impact", error); }
    }
    public static void damage(Entity sword, double amount) {
        try { api(); damage.invoke(sword, amount); }
        catch (ReflectiveOperationException error) { throw new IllegalStateException("Native sword core damage", error); }
    }
    public static void fire(Entity sword, Vec3 direction, float speed) {
        try {
            api(); noClip.invoke(sword, speed == 0);
            ((Projectile) sword).shoot(direction.x, direction.y, direction.z, speed == 0 ? 1f : speed, 0f);
            if (speed == 0) sword.setDeltaMovement(Vec3.ZERO);
        }
        catch (ReflectiveOperationException error) { throw new IllegalStateException("Native phantom release", error); }
    }
    public static void size(Entity drive, float size) {
        try { drive.getClass().getMethod("setBaseSize", float.class).invoke(drive, size); }
        catch (ReflectiveOperationException error) { throw new IllegalStateException("Native Black Fox drive size", error); }
    }
    private BlackFoxPhantomCompat() { }
}
