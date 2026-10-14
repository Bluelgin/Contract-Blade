package com.maidweapon.forge.system.akatsuki;

import com.maidweapon.forge.compat.AkatsukiCombatCompat;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import java.util.*;

/** Basic domain pulse; the adaptive policy extends this single damage entrance in stage B. */
public final class AkatsukiDomainDamage {
    private static boolean applying;
    private static final Map<LivingEntity, Long> NEXT = new WeakHashMap<>();
    public static boolean applying() { return applying; }
    public static boolean domain(DamageSource source) {
        return applying || source.typeHolder().unwrapKey().map(k -> k.location().getNamespace().equals("maid_weapon")
                && k.location().getPath().startsWith("akatsuki_domain_")).orElse(false);
    }
    public static void pulse(ServerPlayer player, LivingEntity target, AkatsukiDomainSession session, boolean adaptive) {
        long now = player.getServer().overworld().getGameTime();
        if (applying || now < NEXT.getOrDefault(target, 0L)) return;
        NEXT.put(target, now + 20);
        applying = true;
        try {
            var key = ResourceKey.create(Registries.DAMAGE_TYPE, AkatsukiDomainGrowth.id("akatsuki_domain_slash"));
            var source = new DamageSource(player.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                    .getHolderOrThrow(key), null, player);
            AkatsukiCombatCompat.guarded(() -> target.hurt(source, .5f));
        } finally { applying = false; }
    }
    public static void clear() { NEXT.clear(); }
    private AkatsukiDomainDamage() {}
}
