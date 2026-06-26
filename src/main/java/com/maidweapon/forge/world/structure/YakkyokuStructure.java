package com.maidweapon.forge.world.structure;

import com.maidweapon.common.MaidWeaponConstants;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import java.util.Optional;

/**
 * Yakkyoku（二式的药房）— 生成在针叶林村庄附近。
 * 条件：地形平坦（高度差 ≤ 3），无高处遮挡。
 * 靠近时：生命恢复 II + 首次进入触发记忆。
 * 结构原点在底部（含地板），直接放在地表。
 */
public class YakkyokuStructure extends Structure {

    public static final ResourceLocation TEMPLATE =
            new ResourceLocation(MaidWeaponConstants.MOD_ID, "yakkyoku");
    private static final int CHECK_RADIUS = 8;

    public static final Codec<YakkyokuStructure> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(settingsCodec(instance))
                    .apply(instance, YakkyokuStructure::new));

    public YakkyokuStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    public Optional<GenerationStub> findGenerationPoint(GenerationContext ctx) {
        BlockPos centerPos = ctx.chunkPos().getMiddleBlockPosition(0);

        int surfaceY = ctx.chunkGenerator().getFirstOccupiedHeight(
                centerPos.getX(), centerPos.getZ(),
                Heightmap.Types.WORLD_SURFACE_WG,
                ctx.heightAccessor(), ctx.randomState());

        // 检查周围地形平坦（高度差 ≤ 3）
        if (!isFlatTerrain(ctx, centerPos, surfaceY)) return Optional.empty();

        // 检查周围无更高的阻挡
        if (hasTallObstacle(ctx, centerPos, surfaceY)) return Optional.empty();

        BlockPos pos = new BlockPos(centerPos.getX(), surfaceY + 1, centerPos.getZ());

        return Optional.of(new GenerationStub(pos, piecesBuilder ->
                piecesBuilder.addPiece(
                        new MaidWeaponStructurePiece(ctx.structureTemplateManager(), TEMPLATE, pos))));
    }

    private boolean isFlatTerrain(GenerationContext ctx, BlockPos center, int surfaceY) {
        for (int dx = -CHECK_RADIUS; dx <= CHECK_RADIUS; dx += 3) {
            for (int dz = -CHECK_RADIUS; dz <= CHECK_RADIUS; dz += 3) {
                int h = ctx.chunkGenerator().getFirstOccupiedHeight(
                        center.getX() + dx, center.getZ() + dz,
                        Heightmap.Types.WORLD_SURFACE_WG,
                        ctx.heightAccessor(), ctx.randomState());
                if (Math.abs(h - surfaceY) > 3) return false;
            }
        }
        return true;
    }

    /** 检查周围是否有比放置面高出 ≥ 6 格的方块（遮挡） */
    private boolean hasTallObstacle(GenerationContext ctx, BlockPos center, int surfaceY) {
        for (int dx = -CHECK_RADIUS; dx <= CHECK_RADIUS; dx += 2) {
            for (int dz = -CHECK_RADIUS; dz <= CHECK_RADIUS; dz += 2) {
                int h = ctx.chunkGenerator().getFirstOccupiedHeight(
                        center.getX() + dx, center.getZ() + dz,
                        Heightmap.Types.WORLD_SURFACE_WG,
                        ctx.heightAccessor(), ctx.randomState());
                if (h > surfaceY + 6) return true;
            }
        }
        return false;
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.YAKKYOKU.get();
    }
}
