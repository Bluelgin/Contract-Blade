package com.maidweapon.forge.system.interior;

import com.maidweapon.common.data.MaidWeaponData;

/**
 * Stable progression profile for one contract interior.
 *
 * <p>Player mastery controls permanent usable-area growth. The warmth stage is
 * retained only for the optional developer/example gallery; real player-owned
 * terrain never inserts furniture from favorability. Favorability is reserved
 * for future maid-at-home behavior.</p>
 */
public record ContractInteriorProfile(int spaceStage, int warmthStage) {
    public static final int MAX_SPACE_STAGE = 5;
    public static final int MAX_WARMTH_STAGE = 6;

    public static ContractInteriorProfile from(int masteryLevel, MaidWeaponData data) {
        int level = Math.max(1, Math.min(masteryLevel, MaidWeaponData.MAX_LEVEL));
        int favorability = Math.max(
                MaidWeaponData.MIN_FAVORABILITY,
                Math.min(data.getFavorability(), MaidWeaponData.MAX_FAVORABILITY)
        );

        int space = Math.min(MAX_SPACE_STAGE, ((level - 1) / 2) + 1);
        int warmth = Math.min(MAX_WARMTH_STAGE, (favorability / 64) + 1);
        return new ContractInteriorProfile(space, warmth);
    }
}
