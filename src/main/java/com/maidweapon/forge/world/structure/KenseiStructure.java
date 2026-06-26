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
 * 剑圣遗迹 — 生成在雪地平原。
 * 条件：周围无水源，地形平坦（高度差 ≤ 3）。
 */
public class KenseiStructure extends Structure {

    public static final ResourceLocation TEMPLATE =
            new ResourceLocation(MaidWeaponConstants.MOD_ID, "kensei");
    private static final int CHECK_RADIUS = 10;

    public static final Codec<KenseiStructure> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(settingsCodec(instance))
                    .apply(instance, KenseiStructure::new));

    public KenseiStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    public Optional<GenerationStub> findGenerationPoint(GenerationContext ctx) {
        BlockPos centerPos = ctx.chunkPos().getMiddleBlockPosition(0);

        int surfaceY = ctx.chunkGenerator().getFirstOccupiedHeight(
                centerPos.getX(), centerPos.getZ(),
                Heightmap.Types.WORLD_SURFACE_WG,
                ctx.heightAccessor(), ctx.randomState());

        // 检查地形平坦度（周围高度差 ≤ 3）
        if (!isFlatTerrain(ctx, centerPos, surfaceY)) return Optional.empty();

        // 检查周围无水源
        if (hasWaterNearby(ctx, centerPos, surfaceY)) return Optional.empty();

        // 结构原点在地表下 11 格
        BlockPos pos = new BlockPos(centerPos.getX(), surfaceY - 11, centerPos.getZ());

        return Optional.of(new GenerationStub(pos, piecesBuilder ->
                piecesBuilder.addPiece(
                        new MaidWeaponStructurePiece(ctx.structureTemplateManager(), TEMPLATE, pos))));
    }

    /** 检查周围地形是否平坦（最大高度差 ≤ 3） */
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

    /** 检查周围是否有水源 */
    private boolean hasWaterNearby(GenerationContext ctx, BlockPos center, int surfaceY) {
        for (int dx = -CHECK_RADIUS; dx <= CHECK_RADIUS; dx += 2) {
            for (int dz = -CHECK_RADIUS; dz <= CHECK_RADIUS; dz += 2) {
                if (dx == 0 && dz == 0) continue;
                int h = ctx.chunkGenerator().getFirstOccupiedHeight(
                        center.getX() + dx, center.getZ() + dz,
                        Heightmap.Types.OCEAN_FLOOR_WG,
                        ctx.heightAccessor(), ctx.randomState());
                // 如果表面高度明显低于地表，说明有湖/河
                int surfaceH = ctx.chunkGenerator().getFirstOccupiedHeight(
                        center.getX() + dx, center.getZ() + dz,
                        Heightmap.Types.WORLD_SURFACE_WG,
                        ctx.heightAccessor(), ctx.randomState());
                if (surfaceH - h >= 2) return true;
            }
        }
        return false;
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.KENSEI.get();
    }
}
