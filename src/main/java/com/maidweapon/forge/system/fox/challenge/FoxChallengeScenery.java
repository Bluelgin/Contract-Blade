package com.maidweapon.forge.system.fox.challenge;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;

/** Versioned scenery only. Never edits the fighting floor, return lamp, player inventory or challenge state. */
final class FoxChallengeScenery {
    static void upgrade(ServerLevel level, BlockPos origin) {
        BlockPos marker = origin.offset(0, -7, 0);
        if (level.getBlockState(marker).is(Blocks.BEDROCK)) return;
        // Break the repeated flat canopies into irregular, layered silhouettes, keeping the same outside trunks.
        for (int i = 0; i < 12; i++) {
            double angle = Math.PI * 2 * i / 12;
            BlockPos foot = origin.offset((int) Math.round(Math.cos(angle) * 31), -4 - i % 3,
                    (int) Math.round(Math.sin(angle) * 31));
            int top = 8 + i % 3;
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                BlockPos old = foot.offset(dx, top, dz);
                if (level.getBlockState(old).is(Blocks.WARPED_WART_BLOCK)) put(level, old, Blocks.AIR);
            }
            for (int layer = -1; layer <= 1; layer++) {
                int spread = layer == 0 ? 3 : 2;
                for (int dx = -spread; dx <= spread; dx++) for (int dz = -spread; dz <= spread; dz++) {
                    if (dx * dx + dz * dz > spread * spread + 1 || Math.floorMod(dx * 7 + dz * 3 + i, 6) == 0) continue;
                    put(level, foot.offset(dx, top + layer, dz), Blocks.WARPED_WART_BLOCK);
                }
            }
            if (level.getBlockState(foot.offset(1, 6, 0)).is(Blocks.SHROOMLIGHT))
                put(level, foot.offset(1, 6, 0), Blocks.SOUL_LANTERN);
        }
        // A broken arc below the invisible floor provides orientation without a visible combat platform.
        for (int i = 0; i < 144; i++) {
            if (i % 18 > 12) continue;
            double angle = Math.PI * 2 * i / 144;
            BlockPos at = origin.offset((int) Math.round(Math.cos(angle) * 22), -3,
                    (int) Math.round(Math.sin(angle) * 22));
            put(level, at, i % 18 == 0 ? Blocks.CRYING_OBSIDIAN : Blocks.POLISHED_BLACKSTONE);
            if (i % 18 == 2) put(level, at.above(), Blocks.SOUL_LANTERN);
        }
        // Sparse distant fragments give depth; all solid decoration remains outside the enclosure or below it.
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * 2 * (i + .3) / 8;
            BlockPos center = origin.offset((int) Math.round(Math.cos(angle) * 43), -10 + i % 4,
                    (int) Math.round(Math.sin(angle) * 43));
            for (int dx = -3; dx <= 3; dx++) for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) + Math.abs(dz) > 4) continue;
                put(level, center.offset(dx, 0, dz), Blocks.BASALT);
                if (Math.floorMod(dx * 3 + dz + i, 4) == 0) put(level, center.offset(dx, -1, dz), Blocks.BLACKSTONE);
            }
            put(level, center.above(), Blocks.SOUL_LANTERN);
        }
        for (int x : new int[]{-3, 3}) {
            put(level, origin.offset(x, 1, 29), Blocks.CRYING_OBSIDIAN);
            put(level, origin.offset(x, 5, 29), Blocks.CRYING_OBSIDIAN);
            put(level, origin.offset(x, 8, 29), Blocks.BLACKSTONE);
        }
        put(level, marker, Blocks.BEDROCK);
    }
    private static void put(ServerLevel level, BlockPos at, Block block) { level.setBlock(at, block.defaultBlockState(), 2 | 16); }
    private FoxChallengeScenery() { }
}
