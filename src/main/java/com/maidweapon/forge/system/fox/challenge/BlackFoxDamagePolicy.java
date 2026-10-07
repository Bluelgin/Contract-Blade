package com.maidweapon.forge.system.fox.challenge;

import com.maidweapon.forge.compat.BlackFoxSlashCompat;
import com.maidweapon.forge.compat.SlashBladeCompat;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/** Phase-two body only. The breakable core deliberately has its own permissive hit policy. */
final class BlackFoxDamagePolicy {
    static boolean bladeMelee(DamageSource source, LivingEntity boss) {
        if (!(source.getEntity() instanceof LivingEntity attacker) || source.is(DamageTypeTags.IS_PROJECTILE)
                || attacker.distanceToSqr(boss) > 36) return false;
        var effect = BlackFoxAttackTrace.projectile();
        if (effect != null && !BlackFoxSlashCompat.nativeSlash(effect)) return false;
        var hit = BlackFoxAttackTrace.current();
        if (effect == null && (source.getDirectEntity() != attacker
                || !source.is(net.minecraft.world.damagesource.DamageTypes.PLAYER_ATTACK)
                && !source.is(net.minecraft.world.damagesource.DamageTypes.MOB_ATTACK))) return false;
        return SlashBladeCompat.isSlashBlade(hit == null ? attacker.getMainHandItem() : hit.originalBlade());
    }
    private BlackFoxDamagePolicy() { }
}
