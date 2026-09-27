package com.maidweapon.forge.system.interior;

import net.minecraft.core.BlockPos;
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
        fill(level, o.offset(-5, 0, -5), o.offset(5, 0, 5), Blocks.SPRUCE_PLANKS);
        buildOpenRoom(level, o.offset(-5, 1, -5), o.offset(5, 5, 5));
        fill(level, o.offset(-6, 6, -6), o.offset(6, 6, 6), Blocks.DARK_OAK_SLAB);
        level.setBlockAndUpdate(o.offset(0, 1, 0), Blocks.LANTERN.defaultBlockState());
    }

    private static void buildAnnex(ServerLevel level, BlockPos o) {
        buildGround(level, o, 12, 20);
        fill(level, o.offset(13, 0, -4), o.offset(19, 0, 4), Blocks.SPRUCE_PLANKS);
        buildOpenRoom(level, o.offset(13, 1, -4), o.offset(19, 5, 4));
        fill(level, o.offset(12, 6, -5), o.offset(20, 6, 5), Blocks.DARK_OAK_SLAB);
        fill(level, o.offset(5, 1, -1), o.offset(13, 3, 1), Blocks.AIR);
        level.setBlockAndUpdate(o.offset(16, 1, 0), Blocks.CRAFTING_TABLE.defaultBlockState());
    }

    private static void buildGarden(ServerLevel level, BlockPos o) {
        buildGround(level, o, 20, 30);
        fill(level, o.offset(-29, -1, -10), o.offset(-21, -1, 10), Blocks.GRASS_BLOCK);
        fill(level, o.offset(-28, -1, -4), o.offset(-23, -1, 4), Blocks.WATER);
        for (int z = -10; z <= 10; z += 2) {
            level.setBlockAndUpdate(o.offset(-21, 0, z), Blocks.OAK_LEAVES.defaultBlockState());
        }
        level.setBlockAndUpdate(o.offset(-22, 0, -8), Blocks.CHERRY_SAPLING.defaultBlockState());
        level.setBlockAndUpdate(o.offset(-22, 0, 8), Blocks.CHERRY_SAPLING.defaultBlockState());
    }

    private static void buildStudy(ServerLevel level, BlockPos o) {
        buildGround(level, o, 30, 40);
        fill(level, o.offset(-5, 0, -39), o.offset(5, 0, -31), Blocks.SPRUCE_PLANKS);
        buildOpenRoom(level, o.offset(-5, 1, -39), o.offset(5, 5, -31));
        fill(level, o.offset(-6, 6, -40), o.offset(6, 6, -30), Blocks.DARK_OAK_SLAB);
        fill(level, o.offset(-3, 1, -38), o.offset(3, 3, -38), Blocks.BOOKSHELF);
        fill(level, o.offset(-1, 1, -30), o.offset(1, 3, -27), Blocks.AIR);
    }

    private static void buildEstate(ServerLevel level, BlockPos o) {
        buildGround(level, o, 40, 52);
        fill(level, o.offset(-7, 0, 41), o.offset(7, 0, 50), Blocks.SPRUCE_PLANKS);
        buildOpenRoom(level, o.offset(-7, 1, 41), o.offset(7, 5, 50));
        fill(level, o.offset(-8, 6, 40), o.offset(8, 6, 51), Blocks.DARK_OAK_SLAB);

        for (int x = -30; x <= 30; x += 6) {
            level.setBlockAndUpdate(o.offset(x, 0, 46), Blocks.CHERRY_LEAVES.defaultBlockState());
        }
        for (int z = -30; z <= 30; z += 6) {
            level.setBlockAndUpdate(o.offset(46, 0, z), Blocks.CHERRY_LEAVES.defaultBlockState());
        }

        // Small pavilion / quiet lookout.
        fill(level, o.offset(42, 0, -46), o.offset(50, 0, -38), Blocks.POLISHED_ANDESITE);
        pillar(level, o.offset(42, 1, -46), 5, Blocks.DARK_OAK_LOG);
        pillar(level, o.offset(50, 1, -46), 5, Blocks.DARK_OAK_LOG);
        pillar(level, o.offset(42, 1, -38), 5, Blocks.DARK_OAK_LOG);
        pillar(level, o.offset(50, 1, -38), 5, Blocks.DARK_OAK_LOG);
        fill(level, o.offset(41, 6, -47), o.offset(51, 6, -37), Blocks.DARK_OAK_SLAB);
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
