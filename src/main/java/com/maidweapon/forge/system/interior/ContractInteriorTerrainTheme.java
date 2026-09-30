package com.maidweapon.forge.system.interior;

import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.Optional;

/** Built-in terrain themes available when a contract interior is first claimed. */
public enum ContractInteriorTerrainTheme {
    PLAINS_GARDEN(
            "plains_garden",
            "maid_weapon.interior.theme.plains_garden",
            "maid_weapon.interior.theme.plains_garden.desc"
    ),
    SAKURA_GARDEN(
            "sakura_garden",
            "maid_weapon.interior.theme.sakura_garden",
            "maid_weapon.interior.theme.sakura_garden.desc"
    ),
    BAMBOO_GROVE(
            "bamboo_grove",
            "maid_weapon.interior.theme.bamboo_grove",
            "maid_weapon.interior.theme.bamboo_grove.desc"
    ),
    LAKE_ISLET(
            "lake_islet",
            "maid_weapon.interior.theme.lake_islet",
            "maid_weapon.interior.theme.lake_islet.desc"
    ),
    HILL_GARDEN(
            "hill_garden",
            "maid_weapon.interior.theme.hill_garden",
            "maid_weapon.interior.theme.hill_garden.desc"
    );

    private final String id;
    private final String titleKey;
    private final String descriptionKey;

    ContractInteriorTerrainTheme(
            String id,
            String titleKey,
            String descriptionKey
    ) {
        this.id = id;
        this.titleKey = titleKey;
        this.descriptionKey = descriptionKey;
    }

    public String id() {
        return id;
    }

    public Component title() {
        return Component.translatable(titleKey);
    }

    public Component description() {
        return Component.translatable(descriptionKey);
    }

    public static Optional<ContractInteriorTerrainTheme> byId(String id) {
        if (id == null || id.isBlank()) return Optional.empty();
        return Arrays.stream(values())
                .filter(theme -> theme.id.equals(id))
                .findFirst();
    }
}
