package com.maidweapon.forge.compat;

import net.minecraftforge.fml.ModList;

/**
 * Compatibility facade for Touhou Little Maid: Spell (万法皆通).
 *
 * <p>Runtime combat behavior lives in {@link TripleMagicCompat}; this facade
 * exposes only capability/presence information kept for older integrations.</p>
 */
public final class WanFaJieTongCompat {
    private static final String MOD_ID = "touhou_little_maid_spell";

    public record WandMaidProfile(boolean ironsSpells, boolean goety, float baseMultiplier) {}

    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    public static boolean isTripleComboAvailable() {
        return TripleMagicCompat.active();
    }

    public static boolean isDoubleComboWithIronSpells() {
        return isLoaded() && IronsSpellsCompat.isLoaded();
    }

    public static boolean isDoubleComboWithGoety() {
        return isLoaded() && GoetyCompat.isLoaded();
    }

    /**
     * Beta API retained with a concrete result rather than a permanent null
     * placeholder. Gameplay still routes through TripleMagicCompat.
     */
    @Deprecated
    public static Object createWandMaidData() {
        if (!isTripleComboAvailable()) return null;
        return new WandMaidProfile(true, true, 1.0f);
    }

    public static float getWandMaidSpellMultiplier(int maidLevel, double loyalty) {
        if (!isLoaded()) return 1.0f;
        float base = 1.0f + (maidLevel - 1) * 0.1f;
        if (loyalty >= 80) base *= 1.2f;
        else if (loyalty < 50) base *= 0.8f;
        return base;
    }

    /** Dynamic tier estimation replaced the old per-mod monster table. */
    @Deprecated
    public static void registerMonsters() {
        // Intentionally empty.
    }

    private WanFaJieTongCompat() {}
}
