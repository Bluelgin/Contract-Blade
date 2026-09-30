package com.maidweapon.forge.system.interior;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * Headless/offline preview layout for the real player-built terrain system.
 *
 * <p>It is deliberately separated from the example-home gallery. The first
 * row shows Sakura Garden progression; the second row compares all five themes
 * at maximum land size.</p>
 */
public final class ContractInteriorTerrainPreview {
    public static final int BASE_X = -16384;
    public static final int BASE_Z = -8192;
    public static final int STAGE_SPACING = 144;
    public static final int THEME_ROW_Z = -8448;
    public static final long PREVIEW_SEED = 0x4D4149445F484F4DL;

    public static BlockPos sakuraStageOrigin(int stage) {
        int safeStage = Math.max(1, Math.min(stage, ContractInteriorProfile.MAX_SPACE_STAGE));
        return new BlockPos(
                BASE_X + ((safeStage - 1) * STAGE_SPACING),
                ContractInteriorTerrainBuilder.ORIGIN_Y,
                BASE_Z
        );
    }

    public static BlockPos themeOrigin(int themeIndex) {
        int safeIndex = Math.max(
                0,
                Math.min(themeIndex, ContractInteriorTerrainTheme.values().length - 1)
        );
        return new BlockPos(
                BASE_X + (safeIndex * STAGE_SPACING),
                ContractInteriorTerrainBuilder.ORIGIN_Y,
                THEME_ROW_Z
        );
    }

    public static boolean generate(MinecraftServer server) {
        if (server == null) return false;
        ServerLevel level = server.getLevel(ContractInteriorService.INTERIOR_LEVEL);
        if (level == null) return false;

        for (int stage = 1; stage <= ContractInteriorProfile.MAX_SPACE_STAGE; stage++) {
            ContractInteriorTerrainBuilder.buildSnapshot(
                    level,
                    sakuraStageOrigin(stage),
                    ContractInteriorTerrainTheme.SAKURA_GARDEN,
                    PREVIEW_SEED,
                    stage,
                    true
            );
        }

        ContractInteriorTerrainTheme[] themes = ContractInteriorTerrainTheme.values();
        for (int index = 0; index < themes.length; index++) {
            ContractInteriorTerrainBuilder.buildSnapshot(
                    level,
                    themeOrigin(index),
                    themes[index],
                    PREVIEW_SEED + index * 9973L,
                    ContractInteriorProfile.MAX_SPACE_STAGE,
                    true
            );
        }
        return true;
    }

    private ContractInteriorTerrainPreview() {
    }
}
