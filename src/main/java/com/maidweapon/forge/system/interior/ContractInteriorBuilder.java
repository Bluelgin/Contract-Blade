package com.maidweapon.forge.system.interior;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.StairBlock;

/**
 * Built-in fallback home for contract interiors.
 *
 * <p>The architecture grows monotonically with contract level. Favorability
 * decorations are reversible, but only touch reserved decoration positions and
 * never overwrite player-placed blocks.</p>
 */
public final class ContractInteriorBuilder {
    public static final int ORIGIN_Y = 80;

    public static void ensureBuilt(
            ServerLevel level,
            BlockPos origin,
            ContractInteriorProfile profile,
            ContractInteriorSavedData saved,
            String bindingId
    ) {
        ContractInteriorSavedData.Plot plot = saved.getOrCreate(bindingId);
        int built = plot.builtSpaceStage();

        for (int stage = built + 1; stage <= profile.spaceStage(); stage++) {
            buildStage(level, origin, stage);
            saved.markBuilt(bindingId, stage);
        }
        applyWarmth(level, origin, profile.warmthStage());
    }

    public static void buildSnapshot(
            ServerLevel level,
            BlockPos origin,
            int stage,
            int warmth,
            boolean clearFirst
    ) {
        int safeStage = Math.max(1, Math.min(stage, ContractInteriorProfile.MAX_SPACE_STAGE));
        int safeWarmth = Math.max(1, Math.min(warmth, ContractInteriorProfile.MAX_WARMTH_STAGE));

        if (clearFirst) {
            clearSnapshotArea(level, origin, safeStage);
        }

        for (int current = 1; current <= safeStage; current++) {
            buildStage(level, origin, current);
        }
        applyWarmth(level, origin, safeWarmth);
    }

    public static void clearSnapshotArea(ServerLevel level, BlockPos origin) {
        clearSnapshotArea(level, origin, ContractInteriorProfile.MAX_SPACE_STAGE);
    }

    private static void clearSnapshotArea(
            ServerLevel level,
            BlockPos origin,
            int stage
    ) {
        int radius = radiusForStage(stage) + 6;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -4; y <= 20; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = origin.offset(x, y, z);
                    if (!level.isEmptyBlock(pos)) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
    }

    private static void buildStage(ServerLevel level, BlockPos o, int stage) {
        switch (stage) {
            case 1 -> buildCore(level, o);
            case 2 -> buildAnnex(level, o);
            case 3 -> buildGarden(level, o);
            case 4 -> buildStudy(level, o);
            case 5 -> buildEstate(level, o);
            default -> {
                return;
            }
        }

        int previousRadius = stage <= 1 ? 0 : radiusForStage(stage - 1);
        if (previousRadius > 0) {
            clearBoundary(level, o, previousRadius);
        }
        buildBoundary(level, o, radiusForStage(stage));
    }

    private static void buildCore(ServerLevel level, BlockPos o) {
        buildGround(level, o, 0, 12);

        buildJapaneseRoom(level, o, -7, -6, 7, 6);
        carveDoor(level, o, 0, 6, Direction.SOUTH);

        // A small stone genkan and warm central room establish the base "home".
        fill(level, o.offset(-2, 0, 7), o.offset(2, 0, 10), Blocks.POLISHED_ANDESITE);
        for (int x = -2; x <= 2; x++) {
            level.setBlockAndUpdate(
                    o.offset(x, -1, 11),
                    Blocks.STONE_BRICK_STAIRS.defaultBlockState()
                            .setValue(StairBlock.FACING, Direction.SOUTH)
            );
        }
        fill(level, o.offset(-5, 0, -3), o.offset(5, 0, 3), Blocks.BIRCH_PLANKS);
        fill(level, o.offset(-1, 0, -3), o.offset(1, 0, 3), Blocks.SPRUCE_PLANKS);
        level.setBlockAndUpdate(o.offset(-5, 1, 4), Blocks.CHEST.defaultBlockState());
        level.setBlockAndUpdate(o.offset(5, 1, 4), Blocks.BARREL.defaultBlockState());

        buildLanternPost(level, o.offset(-4, 0, 9));
        buildLanternPost(level, o.offset(4, 0, 9));
    }

    private static void buildAnnex(ServerLevel level, BlockPos o) {
        buildGround(level, o, 12, 20);

        // Kitchen / work wing.
        buildJapaneseRoom(level, o, 13, -5, 19, 5);
        carveDoor(level, o, 13, 0, Direction.WEST);
        carveDoor(level, o, 7, 0, Direction.EAST);

        // Short covered connector so the kitchen is part of the home even in rain.
        fill(level, o.offset(8, 0, -1), o.offset(12, 0, 1), Blocks.SPRUCE_PLANKS);
        buildCoveredWalkwayRoof(level, o, 8, -2, 12, 2, 4);
        pillar(level, o.offset(9, 1, -2), 3, Blocks.STRIPPED_DARK_OAK_LOG);
        pillar(level, o.offset(11, 1, 2), 3, Blocks.STRIPPED_DARK_OAK_LOG);

        // Covered engawa connecting the original room and the new wing.
        fill(level, o.offset(-8, 0, 7), o.offset(20, 0, 9), Blocks.SPRUCE_PLANKS);
        for (int x = -8; x <= 20; x += 4) {
            pillar(level, o.offset(x, 1, 9), 4, Blocks.STRIPPED_DARK_OAK_LOG);
        }
        buildCoveredWalkwayRoof(level, o, -9, 6, 21, 10, 5);

        level.setBlockAndUpdate(o.offset(15, 1, -2), Blocks.SMOKER.defaultBlockState());
        level.setBlockAndUpdate(o.offset(16, 1, -2), Blocks.FURNACE.defaultBlockState());
        level.setBlockAndUpdate(o.offset(17, 1, -2), Blocks.CRAFTING_TABLE.defaultBlockState());
        level.setBlockAndUpdate(o.offset(18, 1, -2), Blocks.BARREL.defaultBlockState());
        level.setBlockAndUpdate(o.offset(16, 1, 2), Blocks.CAULDRON.defaultBlockState());
    }

    private static void buildGarden(ServerLevel level, BlockPos o) {
        buildGround(level, o, 20, 30);

        // Main stepping-stone approach from the house toward a small torii.
        for (int z = 11; z <= 25; z += 2) {
            level.setBlockAndUpdate(o.offset(0, -1, z), Blocks.MOSSY_STONE_BRICKS.defaultBlockState());
        }
        buildTorii(level, o.offset(0, 0, 26));

        // Pond and planted west garden. Keep the shoreline flush with the
        // ground so it reads as a garden pond rather than a rectangular pool.
        buildPond(level, o, -26, -1, 4, 7);
        paintOuterRingPatch(level, o, 20, 30, -26, -1, 5, 9, 13);

        buildCherryTree(level, o.offset(-25, 0, 13));
        buildCherryTree(level, o.offset(-23, 0, -13));
        buildShrub(level, o.offset(-22, 0, 8));
        buildShrub(level, o.offset(-22, 0, -8));

        level.setBlockAndUpdate(o.offset(-26, 0, -1), Blocks.LILY_PAD.defaultBlockState());
        level.setBlockAndUpdate(o.offset(-24, 0, 2), Blocks.LILY_PAD.defaultBlockState());
        buildLanternPost(level, o.offset(-22, 0, 14));
        scatterOuterRingGroundCover(level, o, 20, 30, 3);
    }

    private static void buildStudy(ServerLevel level, BlockPos o) {
        buildGround(level, o, 30, 40);

        // A quiet north wing: study on the left, tea room on the right.
        buildJapaneseRoom(level, o, -8, -38, 8, -31);
        buildJapaneseRoom(level, o, 12, -38, 22, -31);

        // A narrow garden approach crosses the older rings. Path placement only
        // replaces natural/generated ground, so player construction keeps priority.
        buildPath(level, o, 0, -7, 0, -30, Blocks.MOSSY_STONE_BRICKS);

        carveDoor(level, o, 0, -31, Direction.NORTH);
        carveDoor(level, o, 0, -6, Direction.SOUTH);
        carveDoor(level, o, 12, -34, Direction.WEST);

        // The tea-room side link stays in the newly unlocked ring and remains
        // visually light instead of becoming another large flat roof.
        fill(level, o.offset(3, 0, -35), o.offset(11, 0, -33), Blocks.SPRUCE_PLANKS);
        pillar(level, o.offset(5, 1, -36), 3, Blocks.STRIPPED_DARK_OAK_LOG);
        pillar(level, o.offset(9, 1, -32), 3, Blocks.STRIPPED_DARK_OAK_LOG);
        buildCoveredWalkwayRoof(level, o, 3, -36, 11, -32, 4);

        fill(level, o.offset(-6, 1, -36), o.offset(6, 3, -36), Blocks.BOOKSHELF);
        fill(level, o.offset(-1, 0, -35), o.offset(1, 0, -33), Blocks.RED_CARPET);
        level.setBlockAndUpdate(o.offset(16, 1, -34), Blocks.CAKE.defaultBlockState());
        level.setBlockAndUpdate(o.offset(18, 1, -34), Blocks.POTTED_AZALEA.defaultBlockState());

        // Small karesansui court in the north-west, fully in the new ring.
        fill(level, o.offset(-29, -1, -40), o.offset(-14, -1, -31), Blocks.SAND);
        for (int x = -27; x <= -16; x += 4) {
            fill(level, o.offset(x, 0, -38), o.offset(x + 1, 0, -37), Blocks.SMOOTH_STONE);
        }
        buildShrub(level, o.offset(-27, 0, -32));
        buildShrub(level, o.offset(-16, 0, -32));

        // The Stage 4 wing should feel like a real courtyard destination rather
        // than a detached building at the end of a debug path.
        paintOuterRingPatch(level, o, 30, 40, 0, -34, 12, 5, 17);
        buildLanternPost(level, o.offset(-5, 0, -31));
        buildLanternPost(level, o.offset(5, 0, -31));
        buildShrub(level, o.offset(-11, 0, -32));
        buildShrub(level, o.offset(10, 0, -32));
        scatterOuterRingGroundCover(level, o, 30, 40, 4);
    }

    private static void buildEstate(ServerLevel level, BlockPos o) {
        buildGround(level, o, 40, 52);

        // Southern guest pavilion: the building itself lives entirely in the
        // newly unlocked outer ring.
        buildJapaneseRoom(level, o, 20, 41, 36, 49);
        carveDoor(level, o, 28, 41, Direction.NORTH);
        fill(level, o.offset(18, 0, 38), o.offset(38, 0, 40), Blocks.SPRUCE_PLANKS);
        buildLanternPost(level, o.offset(19, 0, 39));
        buildLanternPost(level, o.offset(37, 0, 39));

        // Northern shrine, also fully in the Stage 5 ring.
        buildJapaneseRoom(level, o, 28, -50, 42, -42);
        carveDoor(level, o, 35, -42, Direction.SOUTH);
        buildTorii(level, o.offset(35, 0, -38));
        fill(level, o.offset(33, 0, -42), o.offset(37, 0, -39), Blocks.POLISHED_ANDESITE);

        // Eastern pond / bridge occupy the new ring instead of replacing the
        // Stage 3 garden. The irregular flush shoreline reads much more naturally
        // in the real-save preview than the old stone-edged rectangle.
        buildPond(level, o, 45, 19, 4, 9);
        fill(level, o.offset(38, 0, 18), o.offset(50, 0, 20), Blocks.SPRUCE_PLANKS);
        for (int x = 40; x <= 50; x += 5) {
            level.setBlockAndUpdate(o.offset(x, 1, 17), Blocks.DARK_OAK_FENCE.defaultBlockState());
            level.setBlockAndUpdate(o.offset(x, 1, 21), Blocks.DARK_OAK_FENCE.defaultBlockState());
        }
        level.setBlockAndUpdate(o.offset(45, 0, 14), Blocks.LILY_PAD.defaultBlockState());
        level.setBlockAndUpdate(o.offset(47, 0, 24), Blocks.LILY_PAD.defaultBlockState());

        buildCherryTree(level, o.offset(-45, 0, 30));
        buildCherryTree(level, o.offset(-28, 0, 45));
        buildCherryTree(level, o.offset(-8, 0, 46));
        buildCherryTree(level, o.offset(8, 0, 46));
        buildCherryTree(level, o.offset(46, 0, 8));
        buildCherryTree(level, o.offset(46, 0, -12));

        // Late-stage paths make the grounds read as one coherent home instead of
        // isolated set pieces. These narrow paths are reserved expansion lanes.
        buildPath(level, o, 0, 11, 0, 18, Blocks.GRAVEL);
        buildPath(level, o, 0, 18, 40, 18, Blocks.GRAVEL);
        buildPath(level, o, 40, 18, 40, 39, Blocks.GRAVEL);
        buildPath(level, o, 40, 39, 28, 39, Blocks.GRAVEL);

        buildPath(level, o, 3, -33, 25, -33, Blocks.MOSSY_STONE_BRICKS);
        buildPath(level, o, 25, -33, 25, -39, Blocks.MOSSY_STONE_BRICKS);
        buildPath(level, o, 25, -39, 35, -39, Blocks.MOSSY_STONE_BRICKS);

        buildPath(level, o, -10, -33, -40, -33, Blocks.COARSE_DIRT);
        buildPath(level, o, -40, -33, -46, -26, Blocks.COARSE_DIRT);

        // Quiet lookout on the far western rim.
        buildOpenPavilion(level, o, -47, -26);

        // Intentional moss gardens anchor the bamboo and cherry grove. Sparse
        // loose ground cover then fills only a few remaining gaps.
        paintOuterRingPatch(level, o, 40, 52, -44, 10, 4, 8, 19);
        paintOuterRingPatch(level, o, 40, 52, 0, 46, 16, 4, 23);
        buildBambooGrove(level, o.offset(-44, 0, 10));
        scatterOuterRingGroundCover(level, o, 40, 52, 5);
    }

    private static void buildJapaneseRoom(
            ServerLevel level,
            BlockPos o,
            int minX,
            int minZ,
            int maxX,
            int maxZ
    ) {
        // Stone sill keeps the timber frame visually separate from the grass.
        fill(level, o.offset(minX, -1, minZ),
                o.offset(maxX, -1, maxZ), Blocks.POLISHED_ANDESITE);
        fill(level, o.offset(minX, 0, minZ),
                o.offset(maxX, 0, maxZ), Blocks.SPRUCE_PLANKS);
        buildTatamiFloor(level, o, minX + 1, minZ + 1, maxX - 1, maxZ - 1);

        for (int x = minX; x <= maxX; x++) {
            for (int z : new int[]{minZ, maxZ}) {
                buildWallColumn(level, o, x, z, minX, minZ, maxX, maxZ);
            }
        }
        for (int z = minZ + 1; z < maxZ; z++) {
            for (int x : new int[]{minX, maxX}) {
                buildWallColumn(level, o, x, z, minX, minZ, maxX, maxZ);
            }
        }

        buildGabledRoof(level, o, minX, minZ, maxX, maxZ);
    }

    private static void buildTatamiFloor(
            ServerLevel level,
            BlockPos o,
            int minX,
            int minZ,
            int maxX,
            int maxZ
    ) {
        if (minX > maxX || minZ > maxZ) return;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                boolean seamX = Math.floorMod(x - minX, 4) == 3;
                boolean seamZ = Math.floorMod(z - minZ, 3) == 2;
                Block block = (seamX || seamZ)
                        ? Blocks.SPRUCE_PLANKS
                        : Blocks.BAMBOO_MOSAIC;
                level.setBlock(
                        o.offset(x, 0, z),
                        block.defaultBlockState(),
                        Block.UPDATE_CLIENTS
                );
            }
        }
    }

    private static void buildCoveredWalkwayRoof(
            ServerLevel level,
            BlockPos o,
            int minX,
            int minZ,
            int maxX,
            int maxZ,
            int baseY
    ) {
        int width = maxX - minX;
        int depth = maxZ - minZ;

        if (width >= depth) {
            int north = minZ;
            int south = maxZ;
            int step = 0;
            while (north + step < south - step) {
                int y = baseY + (step / 2);
                placeRoofRowZ(
                        level, o, minX, maxX, y,
                        north + step, Direction.NORTH
                );
                placeRoofRowZ(
                        level, o, minX, maxX, y,
                        south - step, Direction.SOUTH
                );
                step++;
            }
            int ridgeZ = (north + south) / 2;
            fill(
                    level,
                    o.offset(minX, baseY + 2, ridgeZ),
                    o.offset(maxX, baseY + 2, ridgeZ),
                    Blocks.DEEPSLATE_TILE_SLAB
            );
        } else {
            int west = minX;
            int east = maxX;
            int step = 0;
            while (west + step < east - step) {
                int y = baseY + (step / 2);
                placeRoofRowX(
                        level, o, minZ, maxZ, y,
                        west + step, Direction.WEST
                );
                placeRoofRowX(
                        level, o, minZ, maxZ, y,
                        east - step, Direction.EAST
                );
                step++;
            }
            int ridgeX = (west + east) / 2;
            fill(
                    level,
                    o.offset(ridgeX, baseY + 2, minZ),
                    o.offset(ridgeX, baseY + 2, maxZ),
                    Blocks.DEEPSLATE_TILE_SLAB
            );
        }
    }

    private static void buildGabledRoof(
            ServerLevel level,
            BlockPos o,
            int minX,
            int minZ,
            int maxX,
            int maxZ
    ) {
        int width = maxX - minX;
        int depth = maxZ - minZ;

        if (width >= depth) {
            int north = minZ - 2;
            int south = maxZ + 2;
            int step = 0;
            while (north + step < south - step) {
                int y = 6 + (step / 2);
                int northZ = north + step;
                int southZ = south - step;
                placeRoofRowZ(level, o, minX - 2, maxX + 2, y, northZ, Direction.NORTH);
                placeRoofRowZ(level, o, minX - 2, maxX + 2, y, southZ, Direction.SOUTH);
                step++;
            }

            int ridgeY = 7 + (step / 2);
            int ridgeZ = (north + south) / 2;
            fill(level,
                    o.offset(minX - 1, ridgeY, ridgeZ),
                    o.offset(maxX + 1, ridgeY, ridgeZ),
                    Blocks.DEEPSLATE_TILES);
            fill(level,
                    o.offset(minX - 2, 5, minZ - 2),
                    o.offset(maxX + 2, 5, minZ - 2),
                    Blocks.DEEPSLATE_TILE_SLAB);
            fill(level,
                    o.offset(minX - 2, 5, maxZ + 2),
                    o.offset(maxX + 2, 5, maxZ + 2),
                    Blocks.DEEPSLATE_TILE_SLAB);
            fillGableEndsZ(level, o, minX, maxX, north, south);
        } else {
            int west = minX - 2;
            int east = maxX + 2;
            int step = 0;
            while (west + step < east - step) {
                int y = 6 + (step / 2);
                int westX = west + step;
                int eastX = east - step;
                placeRoofRowX(level, o, minZ - 2, maxZ + 2, y, westX, Direction.WEST);
                placeRoofRowX(level, o, minZ - 2, maxZ + 2, y, eastX, Direction.EAST);
                step++;
            }

            int ridgeY = 7 + (step / 2);
            int ridgeX = (west + east) / 2;
            fill(level,
                    o.offset(ridgeX, ridgeY, minZ - 1),
                    o.offset(ridgeX, ridgeY, maxZ + 1),
                    Blocks.DEEPSLATE_TILES);
            fill(level,
                    o.offset(minX - 2, 5, minZ - 2),
                    o.offset(minX - 2, 5, maxZ + 2),
                    Blocks.DEEPSLATE_TILE_SLAB);
            fill(level,
                    o.offset(maxX + 2, 5, minZ - 2),
                    o.offset(maxX + 2, 5, maxZ + 2),
                    Blocks.DEEPSLATE_TILE_SLAB);
            fillGableEndsX(level, o, minZ, maxZ, west, east);
        }
    }

    private static void fillGableEndsZ(
            ServerLevel level,
            BlockPos o,
            int minX,
            int maxX,
            int north,
            int south
    ) {
        int center = (north + south) / 2;
        for (int z = north + 2; z <= south - 2; z++) {
            int distance = Math.min(z - north, south - z);
            int roofY = 6 + (distance / 2);
            for (int y = 6; y < roofY; y++) {
                Block block = z == center
                        ? Blocks.STRIPPED_DARK_OAK_LOG
                        : Blocks.WHITE_TERRACOTTA;
                level.setBlock(
                        o.offset(minX, y, z),
                        block.defaultBlockState(),
                        Block.UPDATE_CLIENTS
                );
                level.setBlock(
                        o.offset(maxX, y, z),
                        block.defaultBlockState(),
                        Block.UPDATE_CLIENTS
                );
            }
        }
    }

    private static void fillGableEndsX(
            ServerLevel level,
            BlockPos o,
            int minZ,
            int maxZ,
            int west,
            int east
    ) {
        int center = (west + east) / 2;
        for (int x = west + 2; x <= east - 2; x++) {
            int distance = Math.min(x - west, east - x);
            int roofY = 6 + (distance / 2);
            for (int y = 6; y < roofY; y++) {
                Block block = x == center
                        ? Blocks.STRIPPED_DARK_OAK_LOG
                        : Blocks.WHITE_TERRACOTTA;
                level.setBlock(
                        o.offset(x, y, minZ),
                        block.defaultBlockState(),
                        Block.UPDATE_CLIENTS
                );
                level.setBlock(
                        o.offset(x, y, maxZ),
                        block.defaultBlockState(),
                        Block.UPDATE_CLIENTS
                );
            }
        }
    }

    private static void placeRoofRowZ(
            ServerLevel level,
            BlockPos o,
            int minX,
            int maxX,
            int y,
            int z,
            Direction facing
    ) {
        for (int x = minX; x <= maxX; x++) {
            level.setBlock(
                    o.offset(x, y, z),
                    Blocks.DEEPSLATE_TILE_STAIRS.defaultBlockState()
                            .setValue(StairBlock.FACING, facing),
                    Block.UPDATE_CLIENTS
            );
        }
    }

    private static void placeRoofRowX(
            ServerLevel level,
            BlockPos o,
            int minZ,
            int maxZ,
            int y,
            int x,
            Direction facing
    ) {
        for (int z = minZ; z <= maxZ; z++) {
            level.setBlock(
                    o.offset(x, y, z),
                    Blocks.DEEPSLATE_TILE_STAIRS.defaultBlockState()
                            .setValue(StairBlock.FACING, facing),
                    Block.UPDATE_CLIENTS
            );
        }
    }

    private static void buildWallColumn(
            ServerLevel level,
            BlockPos o,
            int x,
            int z,
            int minX,
            int minZ,
            int maxX,
            int maxZ
    ) {
        boolean corner = (x == minX || x == maxX) && (z == minZ || z == maxZ);
        boolean frame = corner
                || (x == minX || x == maxX ? Math.floorMod(z - minZ, 4) == 0
                : Math.floorMod(x - minX, 4) == 0);

        if (frame) {
            pillar(level, o.offset(x, 1, z), 5, Blocks.STRIPPED_DARK_OAK_LOG);
            return;
        }

        level.setBlock(
                o.offset(x, 1, z),
                Blocks.WHITE_TERRACOTTA.defaultBlockState(),
                Block.UPDATE_CLIENTS
        );
        level.setBlock(
                o.offset(x, 2, z),
                Blocks.WHITE_STAINED_GLASS.defaultBlockState(),
                Block.UPDATE_CLIENTS
        );
        level.setBlock(
                o.offset(x, 3, z),
                Blocks.WHITE_STAINED_GLASS.defaultBlockState(),
                Block.UPDATE_CLIENTS
        );
        level.setBlock(
                o.offset(x, 4, z),
                Blocks.WHITE_TERRACOTTA.defaultBlockState(),
                Block.UPDATE_CLIENTS
        );
        level.setBlock(
                o.offset(x, 5, z),
                Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState(),
                Block.UPDATE_CLIENTS
        );
    }

    private static void carveDoor(
            ServerLevel level,
            BlockPos o,
            int x,
            int z,
            Direction direction
    ) {
        clearGeneratedWallBlock(level, o.offset(x, 1, z));
        clearGeneratedWallBlock(level, o.offset(x, 2, z));

        if (direction.getAxis() == Direction.Axis.X) {
            clearGeneratedWallBlock(level, o.offset(x, 1, z + 1));
            clearGeneratedWallBlock(level, o.offset(x, 2, z + 1));
        } else {
            clearGeneratedWallBlock(level, o.offset(x + 1, 1, z));
            clearGeneratedWallBlock(level, o.offset(x + 1, 2, z));
        }
    }

    private static void clearGeneratedWallBlock(ServerLevel level, BlockPos pos) {
        var state = level.getBlockState(pos);
        if (state.is(Blocks.WHITE_TERRACOTTA)
                || state.is(Blocks.WHITE_STAINED_GLASS)
                || state.is(Blocks.STRIPPED_DARK_OAK_LOG)) {
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
    }

    private static void buildTorii(ServerLevel level, BlockPos center) {
        pillar(level, center.offset(-3, 0, 0), 6, Blocks.RED_CONCRETE);
        pillar(level, center.offset(3, 0, 0), 6, Blocks.RED_CONCRETE);
        fill(level, center.offset(-4, 5, 0), center.offset(4, 5, 0), Blocks.RED_CONCRETE);
        fill(level, center.offset(-3, 6, 0), center.offset(3, 6, 0), Blocks.DARK_OAK_LOG);
    }

    private static void buildCherryTree(ServerLevel level, BlockPos base) {
        pillar(level, base, 5, Blocks.CHERRY_LOG);

        // Small horizontal branches make the trunk read through the canopy.
        placeCherryBranch(level, base.offset(1, 3, 0), Direction.Axis.X);
        placeCherryBranch(level, base.offset(-1, 4, 0), Direction.Axis.X);
        placeCherryBranch(level, base.offset(0, 3, 1), Direction.Axis.Z);
        placeCherryBranch(level, base.offset(0, 4, -1), Direction.Axis.Z);

        for (int y = 3; y <= 7; y++) {
            int radius = switch (y) {
                case 3 -> 1;
                case 4 -> 2;
                case 5, 6 -> 3;
                default -> 2;
            };

            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    int distance = x * x + z * z;
                    int edgeNoise = Math.floorMod(
                            (base.getX() + x) * 31 + (base.getZ() + z) * 17 + y * 13,
                            7
                    );
                    if (distance > radius * radius + (edgeNoise == 0 ? 2 : 0)) continue;
                    if (distance > radius * radius && edgeNoise != 0) continue;

                    BlockPos pos = base.offset(x, y, z);
                    if (level.isEmptyBlock(pos)) {
                        level.setBlock(
                                pos,
                                Blocks.CHERRY_LEAVES.defaultBlockState(),
                                Block.UPDATE_CLIENTS
                        );
                    }
                }
            }
        }

        // A few lower hanging leaves soften the silhouette.
        for (BlockPos pos : new BlockPos[]{
                base.offset(2, 3, 1),
                base.offset(-2, 3, -1),
                base.offset(1, 3, -2),
                base.offset(-1, 3, 2)
        }) {
            if (level.isEmptyBlock(pos)) {
                level.setBlock(
                        pos,
                        Blocks.CHERRY_LEAVES.defaultBlockState(),
                        Block.UPDATE_CLIENTS
                );
            }
        }
    }

    private static void placeCherryBranch(
            ServerLevel level,
            BlockPos pos,
            Direction.Axis axis
    ) {
        level.setBlock(
                pos,
                Blocks.CHERRY_LOG.defaultBlockState()
                        .setValue(RotatedPillarBlock.AXIS, axis),
                Block.UPDATE_CLIENTS
        );
    }

    private static void buildShrub(ServerLevel level, BlockPos base) {
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                level.setBlockAndUpdate(base.offset(x, 0, z), Blocks.AZALEA_LEAVES.defaultBlockState());
            }
        }
    }

    private static void buildLanternPost(ServerLevel level, BlockPos base) {
        level.setBlockAndUpdate(base, Blocks.COBBLESTONE_WALL.defaultBlockState());
        level.setBlockAndUpdate(base.above(), Blocks.DARK_OAK_FENCE.defaultBlockState());
        level.setBlockAndUpdate(base.above(2), Blocks.LANTERN.defaultBlockState());
    }

    private static void buildPath(
            ServerLevel level,
            BlockPos o,
            int x1,
            int z1,
            int x2,
            int z2,
            Block block
    ) {
        int x = x1;
        int z = z1;
        Direction.Axis finalAxis = x1 != x2
                ? Direction.Axis.X
                : Direction.Axis.Z;

        while (x != x2) {
            placePathTile(level, o, x, z, block, Direction.Axis.X);
            finalAxis = Direction.Axis.X;
            x += Integer.compare(x2, x);
        }
        while (z != z2) {
            placePathTile(level, o, x, z, block, Direction.Axis.Z);
            finalAxis = Direction.Axis.Z;
            z += Integer.compare(z2, z);
        }

        placePathTile(level, o, x2, z2, block, finalAxis);
    }

    private static void placePathTile(
            ServerLevel level,
            BlockPos o,
            int x,
            int z,
            Block block,
            Direction.Axis travelAxis
    ) {
        placeNaturalPathBlock(level, o.offset(x, -1, z), pathVariation(block, x, z));

        // Alternate one side block so long routes stay walkable but do not read
        // as ruler-straight three-block roads.
        int hash = Math.floorMod(x * 31 + z * 17, 4);
        if (hash != 0) {
            int side = (hash & 1) == 0 ? -1 : 1;
            BlockPos sidePos = travelAxis == Direction.Axis.X
                    ? o.offset(x, -1, z + side)
                    : o.offset(x + side, -1, z);
            placeNaturalPathBlock(level, sidePos, pathVariation(block, x + side, z - side));
        }
    }

    private static Block pathVariation(Block preferred, int x, int z) {
        int hash = Math.floorMod(x * 19 + z * 23, 11);
        if (preferred == Blocks.GRAVEL && hash == 0) return Blocks.COARSE_DIRT;
        if (preferred == Blocks.COARSE_DIRT && hash == 0) return Blocks.GRAVEL;
        if (preferred == Blocks.MOSSY_STONE_BRICKS && hash == 0) {
            return Blocks.MOSSY_COBBLESTONE;
        }
        return preferred;
    }

    private static void placeNaturalPathBlock(
            ServerLevel level,
            BlockPos pos,
            Block block
    ) {
        var state = level.getBlockState(pos);
        if (state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.DIRT)
                || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.GRAVEL)
                || state.is(Blocks.MOSS_BLOCK)
                || state.is(Blocks.MOSSY_STONE_BRICKS)
                || state.is(Blocks.MOSSY_COBBLESTONE)
                || state.is(Blocks.STONE_BRICKS)) {
            level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private static void buildPond(
            ServerLevel level,
            BlockPos o,
            int centerX,
            int centerZ,
            int radiusX,
            int radiusZ
    ) {
        for (int x = centerX - radiusX - 1; x <= centerX + radiusX + 1; x++) {
            for (int z = centerZ - radiusZ - 1; z <= centerZ + radiusZ + 1; z++) {
                BlockPos surface = o.offset(x, -1, z);
                if (!isLandscapeSurface(level, surface)) continue;

                double dx = (x - centerX) / (double) radiusX;
                double dz = (z - centerZ) / (double) radiusZ;
                double distance = dx * dx + dz * dz;
                int noise = Math.floorMod(x * 31 + z * 17, 9);
                double waterLimit = 1.0 + (noise - 4) * 0.015;

                if (distance <= waterLimit) {
                    level.setBlock(
                            surface,
                            Blocks.WATER.defaultBlockState(),
                            Block.UPDATE_CLIENTS
                    );
                    continue;
                }

                if (distance <= 1.35) {
                    Block shore = switch (noise % 4) {
                        case 0 -> Blocks.MOSS_BLOCK;
                        case 1 -> Blocks.GRAVEL;
                        case 2 -> Blocks.MOSSY_COBBLESTONE;
                        default -> Blocks.GRASS_BLOCK;
                    };
                    level.setBlock(surface, shore.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    private static boolean isLandscapeSurface(ServerLevel level, BlockPos pos) {
        var state = level.getBlockState(pos);
        return state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.DIRT)
                || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.GRAVEL)
                || state.is(Blocks.MOSS_BLOCK)
                || state.is(Blocks.SAND);
    }

    private static void scatterOuterRingGroundCover(
            ServerLevel level,
            BlockPos o,
            int previousRadius,
            int radius,
            int salt
    ) {
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (!insideIsland(x, z, radius)
                        || insideIsland(x, z, previousRadius)) {
                    continue;
                }

                int hash = landscapeHash(x, z, salt);
                if (Math.floorMod(hash, 113) > 1) continue;

                BlockPos ground = o.offset(x, -1, z);
                BlockPos plant = o.offset(x, 0, z);
                if (!level.getBlockState(ground).is(Blocks.GRASS_BLOCK)
                        || !level.isEmptyBlock(plant)) {
                    continue;
                }

                Block cover = (hash & 7) == 0
                        ? Blocks.PINK_PETALS
                        : Blocks.MOSS_CARPET;
                level.setBlock(
                        plant,
                        cover.defaultBlockState(),
                        Block.UPDATE_CLIENTS
                );
            }
        }
    }

    private static void paintOuterRingPatch(
            ServerLevel level,
            BlockPos o,
            int previousRadius,
            int radius,
            int centerX,
            int centerZ,
            int radiusX,
            int radiusZ,
            int salt
    ) {
        for (int x = centerX - radiusX; x <= centerX + radiusX; x++) {
            for (int z = centerZ - radiusZ; z <= centerZ + radiusZ; z++) {
                if (!insideIsland(x, z, radius)
                        || insideIsland(x, z, previousRadius)) {
                    continue;
                }

                double dx = (x - centerX) / (double) radiusX;
                double dz = (z - centerZ) / (double) radiusZ;
                if (dx * dx + dz * dz > 1.0) continue;

                BlockPos surface = o.offset(x, -1, z);
                if (!level.getBlockState(surface).is(Blocks.GRASS_BLOCK)) continue;

                int hash = landscapeHash(x, z, salt);
                Block material = switch (Math.floorMod(hash, 13)) {
                    case 0 -> Blocks.COARSE_DIRT;
                    case 1 -> Blocks.GRAVEL;
                    default -> Blocks.MOSS_BLOCK;
                };
                level.setBlock(
                        surface,
                        material.defaultBlockState(),
                        Block.UPDATE_CLIENTS
                );
            }
        }
    }

    private static int landscapeHash(int x, int z, int salt) {
        long value = x * 341873128712L
                + z * 132897987541L
                + salt * 42595009L;
        value ^= value << 13;
        value ^= value >>> 7;
        value ^= value << 17;
        return (int) (value ^ (value >>> 32));
    }

    private static void buildBambooGrove(ServerLevel level, BlockPos base) {
        int[][] stalks = {
                {0, 0, 7},
                {2, 1, 5},
                {-2, 1, 6},
                {1, -2, 6},
                {-1, -3, 5},
                {3, -2, 4},
                {-3, -1, 4}
        };

        for (int[] stalk : stalks) {
            BlockPos root = base.offset(stalk[0], -1, stalk[1]);
            if (level.getBlockState(root).is(Blocks.GRASS_BLOCK)) {
                level.setBlock(
                        root,
                        Blocks.MOSS_BLOCK.defaultBlockState(),
                        Block.UPDATE_CLIENTS
                );
            }
            for (int y = 0; y < stalk[2]; y++) {
                BlockPos pos = base.offset(stalk[0], y, stalk[1]);
                if (level.isEmptyBlock(pos)) {
                    level.setBlock(
                            pos,
                            Blocks.BAMBOO.defaultBlockState(),
                            Block.UPDATE_CLIENTS
                    );
                }
            }
        }
    }

    private static void buildOpenPavilion(
            ServerLevel level,
            BlockPos o,
            int centerX,
            int centerZ
    ) {
        fill(
                level,
                o.offset(centerX - 4, -1, centerZ - 4),
                o.offset(centerX + 4, -1, centerZ + 4),
                Blocks.POLISHED_ANDESITE
        );
        fill(
                level,
                o.offset(centerX - 3, 0, centerZ - 3),
                o.offset(centerX + 3, 0, centerZ + 3),
                Blocks.SPRUCE_PLANKS
        );

        for (int dx : new int[]{-3, 3}) {
            for (int dz : new int[]{-3, 3}) {
                pillar(
                        level,
                        o.offset(centerX + dx, 1, centerZ + dz),
                        5,
                        Blocks.STRIPPED_DARK_OAK_LOG
                );
            }
        }

        // Low railing with a south-facing entrance.
        for (int x = centerX - 2; x <= centerX + 2; x++) {
            level.setBlockAndUpdate(
                    o.offset(x, 1, centerZ - 3),
                    Blocks.DARK_OAK_FENCE.defaultBlockState()
            );
            if (Math.abs(x - centerX) > 1) {
                level.setBlockAndUpdate(
                        o.offset(x, 1, centerZ + 3),
                        Blocks.DARK_OAK_FENCE.defaultBlockState()
                );
            }
        }
        for (int z = centerZ - 2; z <= centerZ + 2; z++) {
            level.setBlockAndUpdate(
                    o.offset(centerX - 3, 1, z),
                    Blocks.DARK_OAK_FENCE.defaultBlockState()
            );
            level.setBlockAndUpdate(
                    o.offset(centerX + 3, 1, z),
                    Blocks.DARK_OAK_FENCE.defaultBlockState()
            );
        }

        buildOpenGabledRoof(
                level,
                o,
                centerX - 3,
                centerZ - 3,
                centerX + 3,
                centerZ + 3
        );
        buildLanternPost(level, o.offset(centerX, 0, centerZ));
    }

    private static void buildOpenGabledRoof(
            ServerLevel level,
            BlockPos o,
            int minX,
            int minZ,
            int maxX,
            int maxZ
    ) {
        int north = minZ - 2;
        int south = maxZ + 2;
        int step = 0;
        while (north + step < south - step) {
            int y = 6 + (step / 2);
            placeRoofRowZ(
                    level,
                    o,
                    minX - 2,
                    maxX + 2,
                    y,
                    north + step,
                    Direction.NORTH
            );
            placeRoofRowZ(
                    level,
                    o,
                    minX - 2,
                    maxX + 2,
                    y,
                    south - step,
                    Direction.SOUTH
            );
            step++;
        }

        int ridgeY = 7 + (step / 2);
        int ridgeZ = (north + south) / 2;
        fill(
                level,
                o.offset(minX - 1, ridgeY, ridgeZ),
                o.offset(maxX + 1, ridgeY, ridgeZ),
                Blocks.DEEPSLATE_TILES
        );
    }

    private static int radiusForStage(int stage) {
        return switch (stage) {
            case 1 -> 12;
            case 2 -> 20;
            case 3 -> 30;
            case 4 -> 40;
            default -> 52;
        };
    }

    private static void buildBoundary(ServerLevel level, BlockPos o, int radius) {
        forEachIslandBoundaryOutside(radius, (x, z) ->
                placeBoundaryIfOpen(level, o.offset(x, 0, z)));
    }

    private static void clearBoundary(ServerLevel level, BlockPos o, int radius) {
        forEachIslandBoundaryOutside(radius, (x, z) ->
                clearGeneratedBoundary(level, o.offset(x, 0, z)));
    }

    private static void forEachIslandBoundaryOutside(
            int radius,
            java.util.function.BiConsumer<Integer, Integer> consumer
    ) {
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (!insideIsland(x, z, radius)) continue;

                int[][] neighbors = {
                        {x + 1, z},
                        {x - 1, z},
                        {x, z + 1},
                        {x, z - 1}
                };
                for (int[] neighbor : neighbors) {
                    if (!insideIsland(neighbor[0], neighbor[1], radius)) {
                        consumer.accept(neighbor[0], neighbor[1]);
                    }
                }
            }
        }
    }

    private static void placeBoundaryIfOpen(ServerLevel level, BlockPos pos) {
        if (level.isEmptyBlock(pos)) {
            level.setBlockAndUpdate(pos, Blocks.BARRIER.defaultBlockState());
        }
        if (level.isEmptyBlock(pos.above())) {
            level.setBlockAndUpdate(pos.above(), Blocks.BARRIER.defaultBlockState());
        }
    }

    private static void clearGeneratedBoundary(ServerLevel level, BlockPos pos) {
        for (int y = 0; y <= 1; y++) {
            BlockPos target = pos.above(y);
            if (level.getBlockState(target).is(Blocks.BARRIER)
                    || level.getBlockState(target).is(Blocks.DARK_OAK_FENCE)) {
                level.setBlockAndUpdate(target, Blocks.AIR.defaultBlockState());
            }
        }
    }

    private static void applyWarmth(ServerLevel level, BlockPos o, int warmth) {
        controlledDecoration(level, o.offset(-2, 1, 2), Blocks.RED_CARPET, warmth >= 2);
        controlledDecoration(level, o.offset(2, 1, 2), Blocks.POTTED_DANDELION, warmth >= 2);
        controlledDecoration(level, o.offset(-4, 1, -3), Blocks.BOOKSHELF, warmth >= 3);
        controlledDecoration(level, o.offset(4, 1, -3), Blocks.BOOKSHELF, warmth >= 3);
        controlledDecoration(level, o.offset(0, 1, 3), Blocks.CAKE, warmth >= 4);
        controlledDecoration(level, o.offset(-3, 2, 0), Blocks.LANTERN, warmth >= 4);
        controlledDecoration(level, o.offset(3, 2, 0), Blocks.LANTERN, warmth >= 4);
        controlledDecoration(level, o.offset(-8, 0, 7), Blocks.PINK_PETALS, warmth >= 5);
        controlledDecoration(level, o.offset(-7, 0, 7), Blocks.PINK_PETALS, warmth >= 5);
        controlledDecoration(level, o.offset(-6, 0, 7), Blocks.PINK_PETALS, warmth >= 5);
        controlledDecoration(level, o.offset(0, 1, -3), Blocks.POTTED_AZALEA, warmth >= 6);
    }

    private static void controlledDecoration(
            ServerLevel level, BlockPos pos, Block desired, boolean enabled) {
        Block current = level.getBlockState(pos).getBlock();
        if (enabled) {
            if (level.isEmptyBlock(pos) || current == desired) {
                level.setBlockAndUpdate(pos, desired.defaultBlockState());
            }
        } else if (current == desired) {
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
    }

    private static void buildGround(
            ServerLevel level,
            BlockPos o,
            int previousRadius,
            int radius
    ) {
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (!insideIsland(x, z, radius)) continue;
                if (previousRadius > 0 && insideIsland(x, z, previousRadius)) {
                    continue;
                }

                level.setBlock(
                        o.offset(x, -3, z),
                        Blocks.DIRT.defaultBlockState(),
                        Block.UPDATE_CLIENTS
                );
                level.setBlock(
                        o.offset(x, -2, z),
                        Blocks.DIRT.defaultBlockState(),
                        Block.UPDATE_CLIENTS
                );
                level.setBlock(
                        o.offset(x, -1, z),
                        Blocks.GRASS_BLOCK.defaultBlockState(),
                        Block.UPDATE_CLIENTS
                );
            }
        }
    }

    private static boolean insideIsland(int x, int z, int radius) {
        if (Math.abs(x) > radius || Math.abs(z) > radius) return false;

        // A high-order superellipse keeps useful cardinal-edge building space
        // while removing the obvious "square platform with clipped corners"
        // silhouette. Small deterministic edge noise prevents a perfect CAD curve.
        double nx = Math.abs(x) / (double) radius;
        double nz = Math.abs(z) / (double) radius;
        double shape = Math.pow(nx, 7.0D) + Math.pow(nz, 7.0D);
        int edgeNoise = Math.floorMod(
                x * 37 + z * 19 + radius * 11,
                9
        ) - 4;
        double threshold = 1.0D + edgeNoise * 0.004D;
        return shape <= threshold;
    }

    private static void buildOpenRoom(ServerLevel level, BlockPos from, BlockPos to) {
        for (int y = from.getY(); y <= to.getY(); y++) {
            for (int x = from.getX(); x <= to.getX(); x++) {
                for (int z = from.getZ(); z <= to.getZ(); z++) {
                    boolean wall = x == from.getX() || x == to.getX()
                            || z == from.getZ() || z == to.getZ();
                    if (!wall) continue;
                    level.setBlockAndUpdate(
                            new BlockPos(x, y, z),
                            (y == from.getY() || y == to.getY()
                                    || x == from.getX() || x == to.getX())
                                    ? Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState()
                                    : Blocks.WHITE_STAINED_GLASS.defaultBlockState()
                    );
                }
            }
        }
        // Simple doorway on the south wall.
        int center = (from.getX() + to.getX()) / 2;
        level.setBlockAndUpdate(new BlockPos(center, from.getY(), to.getZ()), Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(new BlockPos(center, from.getY() + 1, to.getZ()), Blocks.AIR.defaultBlockState());
    }

    private static void pillar(ServerLevel level, BlockPos base, int height, Block block) {
        for (int y = 0; y < height; y++) {
            level.setBlockAndUpdate(base.above(y), block.defaultBlockState());
        }
    }

    private static void fill(ServerLevel level, BlockPos from, BlockPos to, Block block) {
        int minX = Math.min(from.getX(), to.getX());
        int maxX = Math.max(from.getX(), to.getX());
        int minY = Math.min(from.getY(), to.getY());
        int maxY = Math.max(from.getY(), to.getY());
        int minZ = Math.min(from.getZ(), to.getZ());
        int maxZ = Math.max(from.getZ(), to.getZ());
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    level.setBlock(new BlockPos(x, y, z), block.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    private ContractInteriorBuilder() {
    }
}
