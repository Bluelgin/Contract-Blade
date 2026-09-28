package com.maidweapon.forge.system.interior;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;

/**
 * Deterministic, player-edit-safe terrain generator for real contract interiors.
 *
 * <p>Only newly unlocked rings are generated. Existing land is never rebuilt
 * during normal progression, so player construction remains authoritative.</p>
 */
public final class ContractInteriorTerrainBuilder {
    public static final int ORIGIN_Y = ContractInteriorBuilder.ORIGIN_Y;

    public static int radiusForStage(int stage) {
        return switch (Math.max(1, Math.min(stage, ContractInteriorProfile.MAX_SPACE_STAGE))) {
            case 1 -> 16;
            case 2 -> 24;
            case 3 -> 32;
            case 4 -> 40;
            default -> 52;
        };
    }

    public static void ensureGenerated(
            ServerLevel level,
            BlockPos origin,
            ContractInteriorProfile profile,
            ContractInteriorSavedData saved,
            String bindingId
    ) {
        ContractInteriorSavedData.Plot plot = saved.getOrCreate(bindingId);
        ContractInteriorTerrainTheme theme =
                ContractInteriorTerrainTheme.byId(plot.terrainTheme())
                        .orElseThrow(() -> new IllegalStateException(
                                "contract interior terrain has not been selected"));

        int generated = plot.generatedStage();
        for (int stage = generated + 1; stage <= profile.spaceStage(); stage++) {
            if (stage > 1) {
                clearBoundary(
                        level,
                        origin,
                        radiusForStage(stage - 1),
                        plot.terrainSeed()
                );
            }
            generateRing(
                    level,
                    origin,
                    theme,
                    plot.terrainSeed(),
                    stage
            );
            buildBoundary(
                    level,
                    origin,
                    radiusForStage(stage),
                    plot.terrainSeed()
            );
            saved.markGenerated(bindingId, stage);
        }
    }

    public static void buildSnapshot(
            ServerLevel level,
            BlockPos origin,
            ContractInteriorTerrainTheme theme,
            long seed,
            int stage,
            boolean clearFirst
    ) {
        int safeStage = Math.max(1, Math.min(stage, ContractInteriorProfile.MAX_SPACE_STAGE));
        if (clearFirst) {
            clearSnapshotArea(level, origin, radiusForStage(safeStage) + 8);
        }

        for (int current = 1; current <= safeStage; current++) {
            if (current > 1) {
                clearBoundary(level, origin, radiusForStage(current - 1), seed);
            }
            generateRing(level, origin, theme, seed, current);
            buildBoundary(level, origin, radiusForStage(current), seed);
        }
    }

    private static void generateRing(
            ServerLevel level,
            BlockPos origin,
            ContractInteriorTerrainTheme theme,
            long seed,
            int stage
    ) {
        int radius = radiusForStage(stage);
        int previousRadius = stage <= 1 ? 0 : radiusForStage(stage - 1);

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (!insideLand(x, z, radius, seed)) continue;
                if (previousRadius > 0 && insideLand(x, z, previousRadius, seed)) continue;
                generateColumn(level, origin, theme, seed, radius, x, z);
            }
        }

        decorateRing(level, origin, theme, seed, previousRadius, radius);
        if (stage == 1) {
            ensureSpawnClearing(level, origin);
        }
    }

    private static void generateColumn(
            ServerLevel level,
            BlockPos origin,
            ContractInteriorTerrainTheme theme,
            long seed,
            int radius,
            int x,
            int z
    ) {
        int topOffset = terrainHeight(theme, seed, x, z);
        int topY = origin.getY() - 1 + topOffset;
        BlockPos top = new BlockPos(origin.getX() + x, topY, origin.getZ() + z);

        // Preserve anything a player somehow built beyond the old barrier.
        for (int y = origin.getY() - 4; y <= origin.getY() + 5; y++) {
            BlockPos check = new BlockPos(origin.getX() + x, y, origin.getZ() + z);
            if (!level.isEmptyBlock(check)
                    && !level.getBlockState(check).is(Blocks.BARRIER)) {
                return;
            }
        }

        for (int y = origin.getY() - 4; y < topY; y++) {
            level.setBlock(
                    new BlockPos(origin.getX() + x, y, origin.getZ() + z),
                    Blocks.DIRT.defaultBlockState(),
                    Block.UPDATE_CLIENTS
            );
        }

        if (isWaterCell(theme, seed, x, z, radius)) {
            level.setBlock(
                    top,
                    Blocks.WATER.defaultBlockState(),
                    Block.UPDATE_CLIENTS
            );
            return;
        }

        level.setBlock(
                top,
                surfaceBlock(theme, seed, x, z).defaultBlockState(),
                Block.UPDATE_CLIENTS
        );
    }

    private static Block surfaceBlock(
            ContractInteriorTerrainTheme theme,
            long seed,
            int x,
            int z
    ) {
        int hash = hash(seed, x, z, 11);
        return switch (theme) {
            case PLAINS_GARDEN ->
                    Math.floorMod(hash, 29) == 0 ? Blocks.COARSE_DIRT : Blocks.GRASS_BLOCK;
            case SAKURA_GARDEN ->
                    Math.floorMod(hash, 19) == 0 ? Blocks.MOSS_BLOCK : Blocks.GRASS_BLOCK;
            case BAMBOO_GROVE ->
                    Math.floorMod(hash, 13) == 0 ? Blocks.MOSS_BLOCK : Blocks.GRASS_BLOCK;
            case LAKE_ISLET ->
                    Math.floorMod(hash, 23) == 0 ? Blocks.GRAVEL : Blocks.GRASS_BLOCK;
            case HILL_GARDEN ->
                    Math.floorMod(hash, 17) == 0 ? Blocks.COARSE_DIRT : Blocks.GRASS_BLOCK;
        };
    }

    private static int terrainHeight(
            ContractInteriorTerrainTheme theme,
            long seed,
            int x,
            int z
    ) {
        double distance = Math.sqrt((double) x * x + (double) z * z);
        if (distance <= 12.0D) return 0;

        int broad = Math.floorMod(hash(seed, x / 5, z / 5, 23), 7);
        return switch (theme) {
            case PLAINS_GARDEN -> broad == 0 ? 1 : 0;
            case SAKURA_GARDEN -> broad <= 1 ? 1 : 0;
            case BAMBOO_GROVE -> broad == 0 ? 1 : 0;
            case LAKE_ISLET -> 0;
            case HILL_GARDEN -> {
                int ridge = Math.min(2, (int) Math.max(0, (distance - 18.0D) / 13.0D));
                yield Math.max(0, ridge + (broad == 0 ? 1 : 0) - (broad == 6 ? 1 : 0));
            }
        };
    }

    private static boolean isWaterCell(
            ContractInteriorTerrainTheme theme,
            long seed,
            int x,
            int z,
            int radius
    ) {
        if (theme != ContractInteriorTerrainTheme.LAKE_ISLET) return false;
        if (Math.abs(x) <= 10 && Math.abs(z) <= 10) return false;

        double a = ellipse(x, z, 18, -8, 8, 5);
        double b = ellipse(x, z, -19, 13, 6, 9);
        double c = ellipse(x, z, 4, 27, 10, 5);
        int noise = Math.floorMod(hash(seed, x, z, 71), 11) - 5;
        double threshold = 1.0D + noise * 0.025D;
        return (a <= threshold || b <= threshold || c <= threshold)
                && insideLand(x, z, radius, seed);
    }

    private static double ellipse(
            int x,
            int z,
            int centerX,
            int centerZ,
            int radiusX,
            int radiusZ
    ) {
        double dx = (x - centerX) / (double) radiusX;
        double dz = (z - centerZ) / (double) radiusZ;
        return dx * dx + dz * dz;
    }

    private static void decorateRing(
            ServerLevel level,
            BlockPos origin,
            ContractInteriorTerrainTheme theme,
            long seed,
            int previousRadius,
            int radius
    ) {
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (!insideLand(x, z, radius, seed)) continue;
                if (previousRadius > 0 && insideLand(x, z, previousRadius, seed)) continue;
                if (Math.abs(x) <= 9 && Math.abs(z) <= 9) continue;

                BlockPos surface = topSurface(level, origin, x, z);
                if (surface == null || !level.isEmptyBlock(surface.above())) continue;
                int hash = hash(seed, x, z, 101);

                switch (theme) {
                    case PLAINS_GARDEN -> decoratePlains(level, surface, hash);
                    case SAKURA_GARDEN -> decorateSakura(
                            level, origin, surface, seed, x, z,
                            previousRadius, radius, hash);
                    case BAMBOO_GROVE -> decorateBamboo(level, surface, hash);
                    case LAKE_ISLET -> decorateLake(level, surface, hash);
                    case HILL_GARDEN -> decorateHill(level, surface, hash);
                }
            }
        }
    }

    private static void decoratePlains(ServerLevel level, BlockPos surface, int hash) {
        if (!isNaturalLand(level, surface)) return;
        int value = Math.floorMod(hash, 83);
        if (value == 0) {
            level.setBlockAndUpdate(surface.above(), Blocks.DANDELION.defaultBlockState());
        } else if (value == 1) {
            level.setBlockAndUpdate(surface.above(), Blocks.POPPY.defaultBlockState());
        }
    }

    private static void decorateSakura(
            ServerLevel level,
            BlockPos origin,
            BlockPos surface,
            long seed,
            int x,
            int z,
            int previousRadius,
            int radius,
            int hash
    ) {
        if (!isNaturalLand(level, surface)) return;
        int value = Math.floorMod(hash, 313);
        boolean clearOfOldLand = previousRadius <= 0
                || !insideLand(x, z, previousRadius + 4, seed);
        if (value == 0 && clearOfOldLand && clearTreeFootprint(level, surface.above())) {
            buildCherryTree(level, origin, surface.above(), seed, radius);
        } else if (Math.floorMod(hash, 47) == 0) {
            level.setBlockAndUpdate(surface.above(), Blocks.PINK_PETALS.defaultBlockState());
        }
    }

    private static void decorateBamboo(ServerLevel level, BlockPos surface, int hash) {
        if (!isNaturalLand(level, surface)) return;
        if (Math.floorMod(hash, 37) != 0) return;

        int height = 3 + Math.floorMod(hash >>> 4, 5);
        for (int y = 1; y <= height; y++) {
            BlockPos pos = surface.above(y);
            if (!level.isEmptyBlock(pos)) break;
            level.setBlock(pos, Blocks.BAMBOO.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private static void decorateLake(ServerLevel level, BlockPos surface, int hash) {
        if (level.getBlockState(surface).is(Blocks.WATER)) {
            if (Math.floorMod(hash, 31) == 0 && level.isEmptyBlock(surface.above())) {
                level.setBlockAndUpdate(surface.above(), Blocks.LILY_PAD.defaultBlockState());
            }
            return;
        }
        if (isNaturalLand(level, surface) && Math.floorMod(hash, 109) == 0) {
            level.setBlockAndUpdate(surface.above(), Blocks.BLUE_ORCHID.defaultBlockState());
        }
    }

    private static void decorateHill(ServerLevel level, BlockPos surface, int hash) {
        if (!isNaturalLand(level, surface)) return;
        if (Math.floorMod(hash, 127) == 0) {
            level.setBlockAndUpdate(surface.above(), Blocks.AZALEA.defaultBlockState());
        } else if (Math.floorMod(hash, 71) == 0) {
            level.setBlockAndUpdate(surface.above(), Blocks.FERN.defaultBlockState());
        }
    }

    private static void buildCherryTree(
            ServerLevel level,
            BlockPos origin,
            BlockPos base,
            long seed,
            int radius
    ) {
        int trunkHeight = 4 + Math.floorMod(hash(seed, base.getX(), base.getZ(), 211), 3);
        for (int y = 0; y < trunkHeight; y++) {
            BlockPos pos = base.above(y);
            if (!insideLand(
                    pos.getX() - origin.getX(),
                    pos.getZ() - origin.getZ(),
                    radius,
                    seed
            ) || !level.isEmptyBlock(pos)) {
                return;
            }
            level.setBlock(
                    pos,
                    Blocks.CHERRY_LOG.defaultBlockState(),
                    Block.UPDATE_CLIENTS
            );
        }

        BlockPos crown = base.above(trunkHeight - 1);
        placeBranch(level, crown.offset(1, -1, 0), Direction.Axis.X);
        placeBranch(level, crown.offset(-1, -2, 0), Direction.Axis.X);
        placeBranch(level, crown.offset(0, -1, 1), Direction.Axis.Z);

        for (int dy = -1; dy <= 2; dy++) {
            int leafRadius = dy == 2 ? 1 : 2;
            for (int dx = -leafRadius; dx <= leafRadius; dx++) {
                for (int dz = -leafRadius; dz <= leafRadius; dz++) {
                    if (dx * dx + dz * dz > leafRadius * leafRadius + 1) continue;
                    BlockPos pos = crown.offset(dx, dy, dz);
                    int relX = pos.getX() - origin.getX();
                    int relZ = pos.getZ() - origin.getZ();
                    if (!insideLand(relX, relZ, radius, seed) || !level.isEmptyBlock(pos)) {
                        continue;
                    }
                    level.setBlock(
                            pos,
                            Blocks.CHERRY_LEAVES.defaultBlockState(),
                            Block.UPDATE_CLIENTS
                    );
                }
            }
        }
    }

    private static void placeBranch(ServerLevel level, BlockPos pos, Direction.Axis axis) {
        if (!level.isEmptyBlock(pos)) return;
        level.setBlock(
                pos,
                Blocks.CHERRY_LOG.defaultBlockState()
                        .setValue(RotatedPillarBlock.AXIS, axis),
                Block.UPDATE_CLIENTS
        );
    }

    private static boolean clearTreeFootprint(ServerLevel level, BlockPos base) {
        for (int x = -2; x <= 2; x++) {
            for (int y = 0; y <= 7; y++) {
                for (int z = -2; z <= 2; z++) {
                    if (!level.isEmptyBlock(base.offset(x, y, z))) return false;
                }
            }
        }
        return true;
    }

    private static BlockPos topSurface(
            ServerLevel level,
            BlockPos origin,
            int x,
            int z
    ) {
        int worldX = origin.getX() + x;
        int worldZ = origin.getZ() + z;
        for (int y = origin.getY() + 4; y >= origin.getY() - 4; y--) {
            BlockPos pos = new BlockPos(worldX, y, worldZ);
            if (!level.isEmptyBlock(pos) && !level.getBlockState(pos).is(Blocks.BARRIER)) {
                return pos;
            }
        }
        return null;
    }

    private static boolean isNaturalLand(ServerLevel level, BlockPos pos) {
        Block block = level.getBlockState(pos).getBlock();
        return block == Blocks.GRASS_BLOCK
                || block == Blocks.MOSS_BLOCK
                || block == Blocks.COARSE_DIRT
                || block == Blocks.GRAVEL;
    }

    private static void ensureSpawnClearing(ServerLevel level, BlockPos origin) {
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                BlockPos ground = origin.offset(x, -1, z);
                if (level.isEmptyBlock(ground) || level.getBlockState(ground).is(Blocks.WATER)) {
                    level.setBlockAndUpdate(ground, Blocks.GRASS_BLOCK.defaultBlockState());
                }
                for (int y = 0; y <= 5; y++) {
                    BlockPos air = origin.offset(x, y, z);
                    Block block = level.getBlockState(air).getBlock();
                    if (block == Blocks.BAMBOO
                            || block == Blocks.CHERRY_LEAVES
                            || block == Blocks.CHERRY_LOG
                            || block == Blocks.PINK_PETALS
                            || block == Blocks.DANDELION
                            || block == Blocks.POPPY
                            || block == Blocks.BLUE_ORCHID
                            || block == Blocks.AZALEA
                            || block == Blocks.FERN) {
                        level.setBlockAndUpdate(air, Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }
    }

    private static boolean insideLand(int x, int z, int radius, long seed) {
        if (Math.abs(x) > radius || Math.abs(z) > radius) return false;
        double nx = Math.abs(x) / (double) radius;
        double nz = Math.abs(z) / (double) radius;
        double shape = Math.pow(nx, 6.0D) + Math.pow(nz, 6.0D);
        int noise = Math.floorMod(hash(seed, x, z, radius), 9) - 4;
        return shape <= 1.0D + noise * 0.004D;
    }

    private static void buildBoundary(
            ServerLevel level,
            BlockPos origin,
            int radius,
            long seed
    ) {
        forEachBoundaryOutside(radius, seed, (x, z) -> {
            for (int y = 0; y <= 5; y++) {
                BlockPos pos = origin.offset(x, y, z);
                if (level.isEmptyBlock(pos)) {
                    level.setBlock(
                            pos,
                            Blocks.BARRIER.defaultBlockState(),
                            Block.UPDATE_CLIENTS
                    );
                }
            }
        });
    }

    private static void clearBoundary(
            ServerLevel level,
            BlockPos origin,
            int radius,
            long seed
    ) {
        forEachBoundaryOutside(radius, seed, (x, z) -> {
            for (int y = -1; y <= 6; y++) {
                BlockPos pos = origin.offset(x, y, z);
                if (level.getBlockState(pos).is(Blocks.BARRIER)) {
                    level.setBlock(
                            pos,
                            Blocks.AIR.defaultBlockState(),
                            Block.UPDATE_CLIENTS
                    );
                }
            }
        });
    }

    private static void forEachBoundaryOutside(
            int radius,
            long seed,
            java.util.function.BiConsumer<Integer, Integer> consumer
    ) {
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (!insideLand(x, z, radius, seed)) continue;
                int[][] neighbors = {
                        {x + 1, z},
                        {x - 1, z},
                        {x, z + 1},
                        {x, z - 1}
                };
                for (int[] neighbor : neighbors) {
                    if (!insideLand(neighbor[0], neighbor[1], radius, seed)) {
                        consumer.accept(neighbor[0], neighbor[1]);
                    }
                }
            }
        }
    }

    private static void clearSnapshotArea(
            ServerLevel level,
            BlockPos origin,
            int radius
    ) {
        for (int x = -radius; x <= radius; x++) {
            for (int y = -6; y <= 14; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = origin.offset(x, y, z);
                    if (!level.isEmptyBlock(pos)) {
                        level.setBlock(
                                pos,
                                Blocks.AIR.defaultBlockState(),
                                Block.UPDATE_CLIENTS
                        );
                    }
                }
            }
        }
    }

    private static int hash(long seed, int x, int z, int salt) {
        long value = seed
                ^ (x * 341873128712L)
                ^ (z * 132897987541L)
                ^ (salt * 42595009L);
        value ^= value << 13;
        value ^= value >>> 7;
        value ^= value << 17;
        return (int) (value ^ (value >>> 32));
    }

    private ContractInteriorTerrainBuilder() {
    }
}
