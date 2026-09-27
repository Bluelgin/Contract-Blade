package com.maidweapon.forge.system.interior;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

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
            clearSnapshotArea(level, origin);
        }

        for (int current = 1; current <= safeStage; current++) {
            buildStage(level, origin, current);
        }
        applyWarmth(level, origin, safeWarmth);
    }

    public static void clearSnapshotArea(ServerLevel level, BlockPos origin) {
        int radius = 58;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -4; y <= 12; y++) {
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

        // Covered engawa connecting the original room and the new wing.
        fill(level, o.offset(-8, 0, 7), o.offset(20, 0, 9), Blocks.SPRUCE_PLANKS);
        for (int x = -8; x <= 20; x += 4) {
            pillar(level, o.offset(x, 1, 9), 4, Blocks.STRIPPED_DARK_OAK_LOG);
        }
        fill(level, o.offset(-9, 5, 6), o.offset(21, 5, 10), Blocks.DEPSLATE_TILE_SLAB);

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
            level.setBlockAndUpdate(o.offset(0, 0, z), Blocks.MOSSY_STONE_BRICKS.defaultBlockState());
        }
        buildTorii(level, o.offset(0, 0, 26));

        // Pond and planted west garden.
        fill(level, o.offset(-29, -1, -11), o.offset(-18, -1, 9), Blocks.MOSS_BLOCK);
        fill(level, o.offset(-27, -1, -7), o.offset(-20, -1, 5), Blocks.WATER);
        placeStoneEdge(level, o, -28, -8, -19, 6);

        buildCherryTree(level, o.offset(-23, 0, 12));
        buildCherryTree(level, o.offset(-16, 0, -13));
        buildShrub(level, o.offset(-18, 0, 7));
        buildShrub(level, o.offset(-18, 0, -8));

        level.setBlockAndUpdate(o.offset(-24, 0, -1), Blocks.LILY_PAD.defaultBlockState());
        buildLanternPost(level, o.offset(-17, 0, 14));
    }

    private static void buildStudy(ServerLevel level, BlockPos o) {
        buildGround(level, o, 30, 40);

        // A quiet north wing: study on the left, tea room on the right.
        buildJapaneseRoom(level, o, -8, -39, 8, -28);
        buildJapaneseRoom(level, o, 12, -38, 22, -28);

        // Covered north corridor from the original home.
        fill(level, o.offset(-2, 0, -27), o.offset(2, 0, -7), Blocks.SPRUCE_PLANKS);
        for (int z = -27; z <= -7; z += 4) {
            pillar(level, o.offset(-3, 1, z), 4, Blocks.STRIPPED_DARK_OAK_LOG);
            pillar(level, o.offset(3, 1, z), 4, Blocks.STRIPPED_DARK_OAK_LOG);
        }
        fill(level, o.offset(-4, 5, -28), o.offset(4, 5, -6), Blocks.DEPSLATE_TILE_SLAB);

        carveDoor(level, o, 0, -28, Direction.NORTH);
        carveDoor(level, o, 0, -6, Direction.SOUTH);
        carveDoor(level, o, 12, -33, Direction.WEST);

        fill(level, o.offset(-6, 1, -37), o.offset(6, 3, -37), Blocks.BOOKSHELF);
        fill(level, o.offset(-1, 0, -34), o.offset(1, 0, -31), Blocks.RED_CARPET);
        level.setBlockAndUpdate(o.offset(16, 1, -33), Blocks.CAKE.defaultBlockState());
        level.setBlockAndUpdate(o.offset(18, 1, -33), Blocks.POTTED_AZALEA.defaultBlockState());

        // Small karesansui court in the north-west.
        fill(level, o.offset(-29, -1, -39), o.offset(-14, -1, -27), Blocks.SAND);
        for (int x = -27; x <= -16; x += 4) {
            fill(level, o.offset(x, 0, -37), o.offset(x + 1, 0, -36), Blocks.SMOOTH_STONE);
        }
        buildShrub(level, o.offset(-27, 0, -29));
        buildShrub(level, o.offset(-16, 0, -29));
    }

    private static void buildEstate(ServerLevel level, BlockPos o) {
        buildGround(level, o, 40, 52);

        // South-east guest pavilion with its own veranda.
        buildJapaneseRoom(level, o, 27, 27, 43, 39);
        fill(level, o.offset(25, 0, 24), o.offset(45, 0, 26), Blocks.SPRUCE_PLANKS);
        buildLanternPost(level, o.offset(26, 0, 25));
        buildLanternPost(level, o.offset(44, 0, 25));

        // A small shrine in the north-east provides a final destination.
        buildJapaneseRoom(level, o, 29, -48, 43, -38);
        buildTorii(level, o.offset(36, 0, -34));
        fill(level, o.offset(34, 0, -38), o.offset(38, 0, -35), Blocks.POLISHED_ANDESITE);

        // Larger outer pond and bridge tie the late-stage grounds together.
        fill(level, o.offset(12, -1, 18), o.offset(25, -1, 30), Blocks.WATER);
        placeStoneEdge(level, o, 11, 17, 26, 31);
        fill(level, o.offset(20, 0, 20), o.offset(30, 0, 22), Blocks.SPRUCE_PLANKS);
        for (int x = 20; x <= 30; x += 5) {
            level.setBlockAndUpdate(o.offset(x, 1, 19), Blocks.DARK_OAK_FENCE.defaultBlockState());
            level.setBlockAndUpdate(o.offset(x, 1, 23), Blocks.DARK_OAK_FENCE.defaultBlockState());
        }

        buildCherryTree(level, o.offset(-37, 0, 33));
        buildCherryTree(level, o.offset(-26, 0, 42));
        buildCherryTree(level, o.offset(-8, 0, 45));
        buildCherryTree(level, o.offset(8, 0, 45));
        buildCherryTree(level, o.offset(46, 0, 8));
        buildCherryTree(level, o.offset(46, 0, -12));

        // Quiet lookout at the far edge.
        fill(level, o.offset(-46, 0, -48), o.offset(-36, 0, -38), Blocks.POLISHED_ANDESITE);
        pillar(level, o.offset(-45, 1, -47), 5, Blocks.DARK_OAK_LOG);
        pillar(level, o.offset(-37, 1, -47), 5, Blocks.DARK_OAK_LOG);
        pillar(level, o.offset(-45, 1, -39), 5, Blocks.DARK_OAK_LOG);
        pillar(level, o.offset(-37, 1, -39), 5, Blocks.DARK_OAK_LOG);
        fill(level, o.offset(-46, 6, -48), o.offset(-36, 6, -38), Blocks.DEPSLATE_TILE_SLAB);
    }

    private static void buildJapaneseRoom(
            ServerLevel level,
            BlockPos o,
            int minX,
            int minZ,
            int maxX,
            int maxZ
    ) {
        fill(level, o.offset(minX, 0, minZ), o.offset(maxX, 0, maxZ), Blocks.SPRUCE_PLANKS);

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

        // Dark overhanging roof + a raised ridge.
        fill(level, o.offset(minX - 1, 6, minZ - 1),
                o.offset(maxX + 1, 6, maxZ + 1), Blocks.DEPSLATE_TILE_SLAB);
        if ((maxX - minX) >= (maxZ - minZ)) {
            fill(level, o.offset(minX, 7, (minZ + maxZ) / 2),
                    o.offset(maxX, 7, (minZ + maxZ) / 2), Blocks.DEPSLATE_TILES);
        } else {
            fill(level, o.offset((minX + maxX) / 2, 7, minZ),
                    o.offset((minX + maxX) / 2, 7, maxZ), Blocks.DEPSLATE_TILES);
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

        level.setBlock(o.offset(x, 1, z), Blocks.WHITE_TERRACOTTA.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(o.offset(x, 2, z), Blocks.WHITE_STAINED_GLASS.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(o.offset(x, 3, z), Blocks.WHITE_STAINED_GLASS.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(o.offset(x, 4, z), Blocks.WHITE_TERRACOTTA.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(o.offset(x, 5, z), Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState(), Block.UPDATE_CLIENTS);
    }

    private static void carveDoor(
            ServerLevel level,
            BlockPos o,
            int x,
            int z,
            Direction direction
    ) {
        level.setBlockAndUpdate(o.offset(x, 1, z), Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(o.offset(x, 2, z), Blocks.AIR.defaultBlockState());

        if (direction.getAxis() == Direction.Axis.X) {
            level.setBlockAndUpdate(o.offset(x, 1, z + 1), Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(o.offset(x, 2, z + 1), Blocks.AIR.defaultBlockState());
        } else {
            level.setBlockAndUpdate(o.offset(x + 1, 1, z), Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(o.offset(x + 1, 2, z), Blocks.AIR.defaultBlockState());
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
        for (int x = -2; x <= 2; x++) {
            for (int y = 3; y <= 6; y++) {
                for (int z = -2; z <= 2; z++) {
                    if (Math.abs(x) + Math.abs(z) > 3) continue;
                    BlockPos pos = base.offset(x, y, z);
                    if (level.isEmptyBlock(pos)) {
                        level.setBlock(pos, Blocks.CHERRY_LEAVES.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
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

    private static void placeStoneEdge(
            ServerLevel level,
            BlockPos o,
            int minX,
            int minZ,
            int maxX,
            int maxZ
    ) {
        for (int x = minX; x <= maxX; x++) {
            level.setBlockAndUpdate(o.offset(x, 0, minZ), Blocks.COBBLESTONE.defaultBlockState());
            level.setBlockAndUpdate(o.offset(x, 0, maxZ), Blocks.COBBLESTONE.defaultBlockState());
        }
        for (int z = minZ + 1; z < maxZ; z++) {
            level.setBlockAndUpdate(o.offset(minX, 0, z), Blocks.COBBLESTONE.defaultBlockState());
            level.setBlockAndUpdate(o.offset(maxX, 0, z), Blocks.COBBLESTONE.defaultBlockState());
        }
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
        for (int offset = -radius; offset <= radius; offset++) {
            placeFenceIfOpen(level, o.offset(offset, 0, -radius));
            placeFenceIfOpen(level, o.offset(offset, 0, radius));
            placeFenceIfOpen(level, o.offset(-radius, 0, offset));
            placeFenceIfOpen(level, o.offset(radius, 0, offset));
        }
    }

    private static void clearBoundary(ServerLevel level, BlockPos o, int radius) {
        for (int offset = -radius; offset <= radius; offset++) {
            clearGeneratedFence(level, o.offset(offset, 0, -radius));
            clearGeneratedFence(level, o.offset(offset, 0, radius));
            clearGeneratedFence(level, o.offset(-radius, 0, offset));
            clearGeneratedFence(level, o.offset(radius, 0, offset));
        }
    }

    private static void placeFenceIfOpen(ServerLevel level, BlockPos pos) {
        if (level.isEmptyBlock(pos)) {
            level.setBlockAndUpdate(pos, Blocks.DARK_OAK_FENCE.defaultBlockState());
        }
    }

    private static void clearGeneratedFence(ServerLevel level, BlockPos pos) {
        if (level.getBlockState(pos).is(Blocks.DARK_OAK_FENCE)) {
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
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
                if (previousRadius > 0
                        && Math.abs(x) <= previousRadius
                        && Math.abs(z) <= previousRadius) {
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
