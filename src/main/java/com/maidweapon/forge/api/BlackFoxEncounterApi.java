package com.maidweapon.forge.api;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** The Black Fox encounter owns identification; names, models and generic entity tags are not proof. */
public final class BlackFoxEncounterApi {
    public record HitContext(ServerPlayer owner, ItemStack originalBlade) { }

    @FunctionalInterface
    public interface Resolver {
        /** Return null outside a valid encounter or for unrelated attacks.
         * For maid/projectile hits, resolve the real carrier that produced this hit, not today's held item.
         * Never return a projected copy. The encounter must also enforce participation and damage windows.
         */
        HitContext resolve(DamageSource source, LivingEntity target);
    }

    private static Resolver resolver = (source, target) -> null;

    /** Install once during common setup, after registering the actual Boss implementation. */
    public static void setResolver(Resolver encounterResolver) {
        resolver = java.util.Objects.requireNonNull(encounterResolver);
    }

    public static HitContext resolve(DamageSource source, LivingEntity target) {
        return resolver.resolve(source, target);
    }

    private BlackFoxEncounterApi() { }
}
