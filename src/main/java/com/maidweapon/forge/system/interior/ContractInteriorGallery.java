package com.maidweapon.forge.system.interior;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Development gallery containing all five full contract-interior snapshots.
 *
 * <p>The gallery lives entirely in negative coordinates, while normal contract
 * plots are allocated from non-negative coordinates. Normal visits never rebuild
 * a stage; only the explicit rebuild command is destructive.</p>
 */
public final class ContractInteriorGallery {
    private static final int BASE_X = -8192;
    private static final int BASE_Z = -4096;
    private static final int STAGE_SPACING = 144;
    private static final int DEFAULT_WARMTH = 4;

    private static final int WALKWAY_Z = BASE_Z + 72;
    private static final BlockPos OVERVIEW = new BlockPos(
            BASE_X + (2 * STAGE_SPACING),
            ContractInteriorBuilder.ORIGIN_Y,
            WALKWAY_Z
    );

    public static BlockPos overviewSpawn() {
        return OVERVIEW;
    }

    public static BlockPos stageOrigin(int stage) {
        int safeStage = clampStage(stage);
        return new BlockPos(
                BASE_X + ((safeStage - 1) * STAGE_SPACING),
                ContractInteriorBuilder.ORIGIN_Y,
                BASE_Z
        );
    }

    public static BlockPos stageViewingSpawn(int stage) {
        BlockPos origin = stageOrigin(stage);
        int radius = radiusForStage(stage);
        return origin.offset(0, 0, radius + 8);
    }

    public static boolean open(ServerPlayer player) {
        ServerLevel level = interiorLevel(player);
        if (level == null) return false;

        ensureBuilt(level);
        boolean entered = ContractInteriorService.enterGallery(player, overviewSpawn());
        if (entered) {
            player.displayClientMessage(
                    Component.translatable("maid_weapon.message.interior.gallery_entered"),
                    false
            );
        }
        return entered;
    }

    public static boolean visitStage(ServerPlayer player, int stage) {
        ServerLevel level = interiorLevel(player);
        if (level == null) return false;

        ensureBuilt(level);
        boolean entered = ContractInteriorService.enterGallery(
                player,
                stageViewingSpawn(stage)
        );
        if (entered) {
            player.displayClientMessage(
                    Component.translatable(
                            "maid_weapon.message.interior.gallery_stage",
                            clampStage(stage)
                    ),
                    false
            );
        }
        return entered;
    }

    public static boolean rebuild(ServerPlayer player, int warmth) {
        ServerLevel level = interiorLevel(player);
        if (level == null) return false;

        int safeWarmth = rebuild(level, warmth, true);

        boolean entered = ContractInteriorService.enterGallery(player, overviewSpawn());
        if (entered) {
            player.displayClientMessage(
                    Component.translatable(
                            "maid_weapon.message.interior.gallery_rebuilt",
                            safeWarmth
                    ),
                    false
            );
        }
        return entered;
    }

    public static int rebuild(ServerLevel level, int warmth, boolean clearFirst) {
        int safeWarmth = Math.max(
                1,
                Math.min(warmth, ContractInteriorProfile.MAX_WARMTH_STAGE)
        );

        buildScaffold(level, clearFirst);
        for (int stage = 1; stage <= ContractInteriorProfile.MAX_SPACE_STAGE; stage++) {
            BlockPos origin = stageOrigin(stage);
            ContractInteriorBuilder.buildSnapshot(
                    level,
                    origin,
                    stage,
                    safeWarmth,
                    clearFirst
            );
            placeStageMarker(level, origin, stage);
        }
        return safeWarmth;
    }

    public static int rebuild(MinecraftServer server, int warmth, boolean clearFirst) {
        ServerLevel level = server.getLevel(ContractInteriorService.INTERIOR_LEVEL);
        if (level == null) return -1;
        return rebuild(level, warmth, clearFirst);
    }

    public static void ensureBuilt(ServerLevel level) {
        buildScaffold(level, false);
        for (int stage = 1; stage <= ContractInteriorProfile.MAX_SPACE_STAGE; stage++) {
            BlockPos origin = stageOrigin(stage);
            if (isStageBuilt(level, origin)) continue;

            ContractInteriorBuilder.buildSnapshot(
                    level,
                    origin,
                    stage,
                    DEFAULT_WARMTH,
                    false
            );
            placeStageMarker(level, origin, stage);
        }
    }

    private static boolean isStageBuilt(ServerLevel level, BlockPos origin) {
        return level.getBlockState(origin.offset(0, -4, 0)).is(Blocks.LODESTONE);
    }

    private static void placeStageMarker(ServerLevel level, BlockPos origin, int stage) {
        level.setBlockAndUpdate(origin.offset(0, -4, 0), Blocks.LODESTONE.defaultBlockState());

        Block marker = switch (stage) {
            case 1 -> Blocks.LIGHT_BLUE_CONCRETE;
            case 2 -> Blocks.LIME_CONCRETE;
            case 3 -> Blocks.YELLOW_CONCRETE;
            case 4 -> Blocks.ORANGE_CONCRETE;
            default -> Blocks.MAGENTA_CONCRETE;
        };

        int radius = radiusForStage(stage);
        BlockPos gate = origin.offset(0, 0, radius + 3);
        for (int x = -2; x <= 2; x++) {
            level.setBlockAndUpdate(gate.offset(x, -1, 0), marker.defaultBlockState());
        }
        for (int y = 0; y < stage; y++) {
            level.setBlockAndUpdate(gate.offset(0, y, 1), marker.defaultBlockState());
        }
    }

    private static void buildScaffold(ServerLevel level, boolean force) {
        BlockPos marker = OVERVIEW.offset(0, -4, 0);
        if (!force && level.getBlockState(marker).is(Blocks.LODESTONE)) return;

        int firstX = stageOrigin(1).getX() - 24;
        int lastX = stageOrigin(5).getX() + 24;
        int floorY = ContractInteriorBuilder.ORIGIN_Y - 1;

        for (int x = firstX; x <= lastX; x++) {
            for (int z = WALKWAY_Z - 2; z <= WALKWAY_Z + 2; z++) {
                level.setBlock(
                        new BlockPos(x, floorY, z),
                        Blocks.POLISHED_ANDESITE.defaultBlockState(),
                        Block.UPDATE_CLIENTS
                );
            }
            level.setBlock(
                    new BlockPos(x, floorY + 1, WALKWAY_Z - 3),
                    Blocks.DARK_OAK_FENCE.defaultBlockState(),
                    Block.UPDATE_CLIENTS
            );
            level.setBlock(
                    new BlockPos(x, floorY + 1, WALKWAY_Z + 3),
                    Blocks.DARK_OAK_FENCE.defaultBlockState(),
                    Block.UPDATE_CLIENTS
            );
        }

        for (int stage = 1; stage <= ContractInteriorProfile.MAX_SPACE_STAGE; stage++) {
            BlockPos origin = stageOrigin(stage);
            int startZ = origin.getZ() + radiusForStage(stage) + 4;
            for (int z = startZ; z <= WALKWAY_Z; z++) {
                for (int x = -1; x <= 1; x++) {
                    level.setBlock(
                            new BlockPos(origin.getX() + x, floorY, z),
                            Blocks.STONE_BRICKS.defaultBlockState(),
                            Block.UPDATE_CLIENTS
                    );
                }
            }

            // A lantern pair makes each stage entrance readable at a glance.
            BlockPos post = new BlockPos(origin.getX(), floorY + 1, WALKWAY_Z - 1);
            level.setBlockAndUpdate(post.offset(-2, 0, 0), Blocks.DARK_OAK_FENCE.defaultBlockState());
            level.setBlockAndUpdate(post.offset(2, 0, 0), Blocks.DARK_OAK_FENCE.defaultBlockState());
            level.setBlockAndUpdate(post.offset(-2, 1, 0), Blocks.LANTERN.defaultBlockState());
            level.setBlockAndUpdate(post.offset(2, 1, 0), Blocks.LANTERN.defaultBlockState());
        }

        level.setBlockAndUpdate(marker, Blocks.LODESTONE.defaultBlockState());
    }

    private static ServerLevel interiorLevel(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        return server == null ? null : server.getLevel(ContractInteriorService.INTERIOR_LEVEL);
    }

    private static int radiusForStage(int stage) {
        return switch (clampStage(stage)) {
            case 1 -> 12;
            case 2 -> 20;
            case 3 -> 30;
            case 4 -> 40;
            default -> 52;
        };
    }

    private static int clampStage(int stage) {
        return Math.max(1, Math.min(stage, ContractInteriorProfile.MAX_SPACE_STAGE));
    }

    private ContractInteriorGallery() {
    }
}
