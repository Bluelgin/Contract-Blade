package com.maidweapon.forge.compat;

import com.maidweapon.forge.api.ability.ContractAbilityRegistry;
import com.maidweapon.forge.system.fox.BlackFoxSwordCoreGrowth;

/** Optional SlashBlade bridge: independent reward permission plus declarative shared mastery growth. */
public final class BlackFoxAbilityAdapter {
    public static void register() {
        if (!SlashBladeCompat.isLoaded()) return;
        ContractAbilityRegistry.register(BlackFoxSwordArtCompat.ID,
                (player, carrier) -> BlackFoxSwordArtCompat.progressionEntitled(carrier, player),
                BlackFoxSwordCoreGrowth.FEATURES, BlackFoxSwordCoreGrowth.PARAMETERS, BlackFoxSwordCoreGrowth.fallback());
    }
    private BlackFoxAbilityAdapter() { }
}
