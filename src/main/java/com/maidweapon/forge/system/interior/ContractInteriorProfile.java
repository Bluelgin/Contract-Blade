package com.maidweapon.forge.system.interior;

import com.maidweapon.common.data.MaidWeaponData;

/**
 * Stable visual/progression profile for one contract interior.
 *
 * <p>Contract level controls permanent spatial growth. Favorability controls
 * reversible lived-in decoration. Resonance is deliberately not persisted here
 * so short-term combat state cannot erase home progression.</p>
 */
public record ContractInteriorProfile(int spaceStage, int warmthStage) {
    public static final int MAX_SPACE_STAGE = 5;
    public static final int MAX_WARMTH_STAGE = 6;

    public static ContractInteriorProfile from(MaidWeaponData data) {
        int level = Math.max(1, Math.min(data.getLevel(), MaidWeaponData.MAX_LEVEL));
        int favorability = Math.max(
                MaidWeaponData.MIN_FAVORABILITY,
                Math.min(data.getFavorability(), MaidWeaponData.MAX_FAVORABILITY)
        );

        int space = Math.min(MAX_SPACE_STAGE, ((level - 1) / 2) + 1);
        int warmth = Math.min(MAX_WARMTH_STAGE, (favorability / 64) + 1);
        return new ContractInteriorProfile(space, warmth);
    }
}
