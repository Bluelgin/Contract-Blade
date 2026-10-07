package com.maidweapon.forge.system.fox.challenge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;

/** An invisible fighting floor. Scenery is outside the playable enclosure. */
public final class FoxChallengeArena {
    public static final int FLOOR_Y = 63;
    public static final int RADIUS = 24;
    public static final int SPACING = 256;

    public static BlockPos origin(int index) {
        return new BlockPos((index % 4096) * SPACING, FLOOR_Y, (index / 4096) * SPACING);
    }

    public static BlockPos entry(BlockPos origin) { return origin.offset(0, 1, -19); }
    public static BlockPos returnLight(BlockPos origin) { return origin.offset(0, 1, -22); }
    public static BlockPos bossSpawn(BlockPos origin) { return origin.offset(0, 1, 12); }

    public static boolean contains(BlockPos origin, double x, double y, double z) {
        return Math.abs(x - origin.getX() - 0.5) < RADIUS
                && Math.abs(z - origin.getZ() - 0.5) < RADIUS
                && y >= FLOOR_Y && y < FLOOR_Y + 18;
    }

    public static void build(ServerLevel level, BlockPos origin) {
        // Marker is outside the arena. Re-entry/restart never regenerates an existing cell.
        BlockPos marker = origin.offset(0, -6, 0);
        if (level.getBlockState(marker).is(Blocks.BEDROCK)) { FoxChallengeScenery.upgrade(level, origin); return; }
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                place(level, origin.offset(x, 0, z), Blocks.BARRIER);
                if (Math.abs(x) == RADIUS || Math.abs(z) == RADIUS) {
                    for (int y = 1; y <= 18; y++) place(level, origin.offset(x, y, z), Blocks.BARRIER);
                }
                place(level, origin.offset(x, 19, z), Blocks.BARRIER);
            }
        }
        // Cold fragments below the transparent floor, not a second visible fighting floor.
        for (int i = 0; i < 12; i++) {
            double angle = Math.PI * 2 * i / 12;
            int x = (int) Math.round(Math.cos(angle) * 31);
            int z = (int) Math.round(Math.sin(angle) * 31);
            BlockPos foot = origin.offset(x, -4 - i % 3, z);
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++)
                place(level, foot.offset(dx, 0, dz), Blocks.BASALT);
            for (int y = 1; y <= 7 + i % 3; y++)
                level.setBlock(foot.above(y), Blocks.WARPED_STEM.defaultBlockState()
                        .setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y), 2 | 16);
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++)
                if (Math.abs(dx) + Math.abs(dz) < 4)
                    place(level, foot.offset(dx, 8 + i % 3, dz), Blocks.WARPED_WART_BLOCK);
            place(level, foot.offset(1, 6, 0), Blocks.SHROOMLIGHT);
        }
        place(level, returnLight(origin), Blocks.SOUL_LANTERN);
        // The sealed passage is behind the future Boss, beyond the enclosure.
        for (int x = -3; x <= 3; x++) for (int y = 0; y <= 7; y++)
            if (Math.abs(x) == 3 || y == 7) place(level, origin.offset(x, y, 29), Blocks.POLISHED_BLACKSTONE);
        for (int x = -2; x <= 2; x++) for (int y = 1; y <= 6; y++)
            place(level, origin.offset(x, y, 29), Blocks.CYAN_STAINED_GLASS);
        place(level, marker, Blocks.BEDROCK);
        FoxChallengeScenery.upgrade(level, origin);
    }

    public static void atmosphere(ServerLevel level, BlockPos origin) {
        for (int i = 0; i < 8; i++) {
            int offset = -21 + i * 6;
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, origin.getX() + offset + 0.5,
                    FLOOR_Y + 1.05, origin.getZ() - 23, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, origin.getX() + offset + 0.5,
                    FLOOR_Y + 1.05, origin.getZ() + 23, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, origin.getX() - 23,
                    FLOOR_Y + 1.05, origin.getZ() + offset + 0.5, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, origin.getX() + 23,
                    FLOOR_Y + 1.05, origin.getZ() + offset + 0.5, 1, 0, 0, 0, 0);
        }
        // Slow drifting souls around the perimeter, not a curtain across the duel's sight lines.
        double phase = level.getGameTime() * .002;
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * 2 * i / 8 + phase;
            level.sendParticles(ParticleTypes.SOUL, origin.getX() + .5 + Math.cos(angle) * 21,
                    FLOOR_Y + 1.8 + Math.sin(angle * 2) * .4, origin.getZ() + .5 + Math.sin(angle) * 21,
                    1, .15, .2, .15, .005);
        }
    }

    private static void place(ServerLevel level, BlockPos pos, net.minecraft.world.level.block.Block block) {
        level.setBlock(pos, block.defaultBlockState(), 2 | 16);
    }

    private FoxChallengeArena() { }
}
