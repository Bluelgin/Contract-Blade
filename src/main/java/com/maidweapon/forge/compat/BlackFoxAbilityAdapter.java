package com.maidweapon.forge.compat;

import com.maidweapon.forge.api.ability.ContractAbilityRegistry;
import java.util.Map;
import java.util.Set;

/** Optional SlashBlade bridge. Phase one registers permission only, with no execution gate or growth route. */
public final class BlackFoxAbilityAdapter {
    public static void register() {
        if (!SlashBladeCompat.isLoaded()) return;
        ContractAbilityRegistry.register(BlackFoxSwordArtCompat.ID,
                (player, carrier) -> BlackFoxSwordArtCompat.progressionEntitled(carrier, player),
                Set.of(), Map.of(), null);
    }
    private BlackFoxAbilityAdapter() { }
}
