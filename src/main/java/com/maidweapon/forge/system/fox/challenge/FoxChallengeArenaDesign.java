package com.maidweapon.forge.system.fox.challenge;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** One-time arena upgrade, confined to generated cells; never changes encounter bounds or player data. */
final class FoxChallengeArenaDesign {
    static void upgrade(ServerLevel level, BlockPos origin) {
        BlockPos marker = origin.offset(0, -8, 0);
        if (level.getBlockState(marker).is(Blocks.BEDROCK)) return;
        // Remove only the known generated teal trees, not arbitrary blocks or an entire dimension.
        for (int i = 0; i < 12; i++) {
            double angle = Math.PI * 2 * i / 12;
            BlockPos foot = origin.offset((int) Math.round(Math.cos(angle) * 31), -4 - i % 3,
                    (int) Math.round(Math.sin(angle) * 31));
            for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) for (int y = 0; y <= 12; y++) {
                BlockPos at = foot.offset(x, y, z);
                var state = level.getBlockState(at);
                if (state.is(Blocks.WARPED_STEM) || state.is(Blocks.WARPED_WART_BLOCK)
                        || state.is(Blocks.SHROOMLIGHT) || state.is(Blocks.SOUL_LANTERN)
                        || state.is(Blocks.BASALT)) put(level, at, Blocks.AIR);
            }
        }
        // Octagonal outer silhouette contains the entire original square combat floor.
        for (int x = -32; x <= 32; x++) for (int z = -32; z <= 32; z++) {
            int edge = Math.max(Math.abs(x), Math.abs(z));
            int diagonal = Math.abs(x) + Math.abs(z);
            if (diagonal > 56) continue;
            double radius = Math.sqrt(x * x + z * z);
            Block floor = Blocks.POLISHED_BLACKSTONE;
            if (edge >= 30 || diagonal >= 54) floor = Blocks.POLISHED_DEEPSLATE;
            else if (edge == 24 || radius >= 17 && radius < 18) floor = Blocks.SMOOTH_BASALT;
            else if (radius >= 7 && radius < 8) floor = Blocks.CHISELED_POLISHED_BLACKSTONE;
            else if (radius < 3) floor = Blocks.OBSIDIAN;
            // Sparse accents only: purple skill warnings must remain legible.
            if (radius >= 7 && radius < 8 && (x == 0 || z == 0)) floor = Blocks.CRYING_OBSIDIAN;
            put(level, origin.offset(x, 0, z), floor);
            put(level, origin.offset(x, -1, z), Blocks.DEEPSLATE_BRICKS);
            if (edge >= 30 || diagonal >= 54) put(level, origin.offset(x, -2, z), Blocks.POLISHED_BLACKSTONE);
        }
        // Four stepped spectator banks. All raised blocks remain beyond the barrier walls.
        for (int side = 0; side < 4; side++) for (int row = 0; row < 3; row++)
            for (int along = -15; along <= 15; along++) {
                if (Math.abs(along) < 5) continue; // Preserve entrance and rear gateway sight lines.
                for (int y = 0; y <= row + 1; y++) {
                    int distance = 34 + row * 2;
                    int x = side < 2 ? along : (side == 2 ? -distance : distance);
                    int z = side < 2 ? (side == 0 ? -distance : distance) : along;
                    put(level, origin.offset(x, y, z), y == row + 1 ? Blocks.POLISHED_DEEPSLATE : Blocks.DEEPSLATE_BRICKS);
                }
            }
        // Broken obelisks frame the arena without obstructing fighting or final-core targeting.
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * 2 * (i + .5) / 8;
            int x = (int) Math.round(Math.cos(angle) * 39), z = (int) Math.round(Math.sin(angle) * 39);
            int height = 6 + i % 4;
            for (int y = 0; y < height; y++) for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
                if (y < height - 2 || dx + dz != 2)
                    put(level, origin.offset(x + dx, y, z + dz), y % 3 == 0 ? Blocks.CHISELED_POLISHED_BLACKSTONE : Blocks.OBSIDIAN);
            put(level, origin.offset(x, height, z), Blocks.SOUL_LANTERN);
        }
        // Suspended fragments below the spectator banks reinforce the floating silhouette.
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * 2 * (i + .3) / 8;
            BlockPos fragment = origin.offset((int) Math.round(Math.cos(angle) * 43), -10 + i % 4,
                    (int) Math.round(Math.sin(angle) * 43));
            for (int x = -3; x <= 3; x++) for (int z = -2; z <= 2; z++) {
                if (Math.abs(x) + Math.abs(z) > 4) continue;
                put(level, fragment.offset(x, 0, z), Blocks.POLISHED_DEEPSLATE);
                if (Math.floorMod(x * 3 + z + i, 4) == 0)
                    put(level, fragment.offset(x, -1, z), Blocks.BLACKSTONE);
            }
        }
        // An outside entrance arch; the functional return lamp keeps its original coordinate.
        for (int x : new int[]{-4, 4}) for (int y = 1; y <= 7; y++)
            put(level, origin.offset(x, y, -29), Blocks.DEEPSLATE_BRICKS);
        for (int x = -4; x <= 4; x++) put(level, origin.offset(x, 8, -29), Blocks.CHISELED_POLISHED_BLACKSTONE);
        for (int x = -2; x <= 2; x++) for (int y = 1; y <= 6; y++)
            if (level.getBlockState(origin.offset(x, y, 29)).is(Blocks.CYAN_STAINED_GLASS))
                put(level, origin.offset(x, y, 29), Blocks.PURPLE_STAINED_GLASS);
        put(level, FoxChallengeArena.returnLight(origin), Blocks.SOUL_LANTERN);
        put(level, marker, Blocks.BEDROCK);
    }
    private static void put(ServerLevel level, BlockPos pos, Block block) {
        level.setBlock(pos, block.defaultBlockState(), 2 | 16);
    }
    private FoxChallengeArenaDesign() { }
}
