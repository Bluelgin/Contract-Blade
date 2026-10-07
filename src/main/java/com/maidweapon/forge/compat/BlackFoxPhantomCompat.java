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
    private static Method color, damage, shoot, hit, noClip;
    private static void api() throws ReflectiveOperationException {
        if (hit != null) return;
        var type = Class.forName("mods.flammpfeil.slashblade.entity.EntityAbstractSummonedSword");
        color = type.getMethod("setColor", int.class); damage = type.getMethod("setDamage", double.class);
        shoot = type.getMethod("shoot", double.class, double.class, double.class, float.class, float.class);
        hit = type.getMethod("doForceHitEntity", Entity.class);
        noClip = type.getMethod("setNoClip", boolean.class);
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
            color.invoke(entity, 0xB04CE5); damage.invoke(entity, blade ? 8.0 : 5.0);
            if (blade) {
                entity.getClass().getMethod("setLifetime", float.class).invoke(entity, 60f);
                entity.getClass().getMethod("setBaseSize", float.class).invoke(entity, 1.15f);
            }
            shoot.invoke(entity, direction.x, direction.y, direction.z, speed == 0 ? 1f : speed, 0f);
            if (speed == 0) { entity.setDeltaMovement(Vec3.ZERO); entity.setNoGravity(true); noClip.invoke(entity, true); }
            owner.level().addFreshEntity(entity);
            return entity;
        } catch (ReflectiveOperationException error) { throw new IllegalStateException("Native Black Fox phantom sword", error); }
    }
    public static void impact(Entity sword, Entity target) {
        try { api(); hit.invoke(sword, target); }
        catch (ReflectiveOperationException error) { throw new IllegalStateException("Native phantom impact", error); }
    }
    public static void fire(Entity sword, Vec3 direction, float speed) {
        try {
            api(); noClip.invoke(sword, speed == 0);
            shoot.invoke(sword, direction.x, direction.y, direction.z, speed == 0 ? 1f : speed, 0f);
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
